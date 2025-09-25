use async_trait::async_trait;
use sqlx::PgPool;
use common::database::CrudStorage;
use common::error::{AppError, AppResult};
use crate::models::{Payment, Account, LockResult, WriteOffResult};
use chrono::Utc;

/// Payment storage implementation (mirrors Java PaymentStorage)
pub struct PaymentStorage {
    pool: PgPool,
}

impl PaymentStorage {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl CrudStorage<Payment, String> for PaymentStorage {
    async fn save(&self, entity: Payment) -> AppResult<Payment> {
        sqlx::query!(
            r#"
            INSERT INTO payments (id, external_ref, client_id, amount, insufficient, status, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            ON CONFLICT (id) DO UPDATE SET
                status = $6, insufficient = $5, updated_at = $8
            "#,
            entity.id,
            entity.external_ref,
            entity.client_id,
            entity.amount,
            entity.insufficient,
            entity.status as _,
            entity.created_at,
            entity.updated_at
        )
        .execute(&self.pool)
        .await?;

        Ok(entity)
    }

    async fn get_by_id(&self, id: String) -> AppResult<Payment> {
        let payment = sqlx::query_as!(
            Payment,
            r#"
            SELECT id, external_ref, client_id, amount, insufficient, 
                   status as "status: _", created_at, updated_at
            FROM payments WHERE id = $1
            "#,
            id
        )
        .fetch_optional(&self.pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Payment with id {} not found", id)))?;

        Ok(payment)
    }

    async fn find_all(&self) -> AppResult<Vec<Payment>> {
        let payments = sqlx::query_as!(
            Payment,
            r#"
            SELECT id, external_ref, client_id, amount, insufficient,
                   status as "status: _", created_at, updated_at
            FROM payments ORDER BY created_at DESC
            "#
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(payments)
    }

    async fn delete_by_id(&self, id: String) -> AppResult<()> {
        let result = sqlx::query!("DELETE FROM payments WHERE id = $1", id)
            .execute(&self.pool)
            .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::NotFound(format!("Payment with id {} not found", id)));
        }

        Ok(())
    }
}

/// Account storage implementation (mirrors Java AccountStorage)
pub struct AccountStorage {
    pool: PgPool,
}

impl AccountStorage {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Add lock to account balance (reserve funds)
    pub async fn add_lock(&self, client_id: &str, amount: f64) -> AppResult<LockResult> {
        let mut tx = self.pool.begin().await?;

        let account = sqlx::query_as!(
            Account,
            "SELECT client_id, amount, locked, updated_at FROM accounts WHERE client_id = $1 FOR UPDATE",
            client_id
        )
        .fetch_optional(&mut *tx)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Account {} not found", client_id)))?;

        let available = account.available_balance();
        if available >= amount {
            // Sufficient funds - add lock
            sqlx::query!(
                "UPDATE accounts SET locked = locked + $1, updated_at = $2 WHERE client_id = $3",
                amount,
                Utc::now(),
                client_id
            )
            .execute(&mut *tx)
            .await?;

            tx.commit().await?;
            Ok(LockResult {
                success: true,
                insufficient_amount: 0.0,
            })
        } else {
            // Insufficient funds
            tx.rollback().await?;
            Ok(LockResult {
                success: false,
                insufficient_amount: amount - available,
            })
        }
    }

    /// Remove lock from account balance
    pub async fn unlock(&self, client_id: &str, amount: f64) -> AppResult<()> {
        sqlx::query!(
            "UPDATE accounts SET locked = GREATEST(0, locked - $1), updated_at = $2 WHERE client_id = $3",
            amount,
            Utc::now(),
            client_id
        )
        .execute(&self.pool)
        .await?;

        Ok(())
    }

    /// Write off funds from account (complete payment)
    pub async fn write_off(&self, client_id: &str, amount: f64) -> AppResult<WriteOffResult> {
        let mut tx = self.pool.begin().await?;

        let account = sqlx::query_as!(
            Account,
            "SELECT client_id, amount, locked, updated_at FROM accounts WHERE client_id = $1 FOR UPDATE",
            client_id
        )
        .fetch_optional(&mut *tx)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Account {} not found", client_id)))?;

        if account.locked < amount {
            tx.rollback().await?;
            return Err(AppError::InvalidState(format!(
                "Insufficient locked funds: {} < {}",
                account.locked, amount
            )));
        }

        let timestamp = Utc::now();
        let new_balance = account.amount - amount;

        sqlx::query!(
            "UPDATE accounts SET amount = $1, locked = locked - $2, updated_at = $3 WHERE client_id = $4",
            new_balance,
            amount,
            timestamp,
            client_id
        )
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;

        Ok(WriteOffResult {
            balance: new_balance,
            timestamp,
        })
    }

    /// Add amount to account balance (top up)
    pub async fn add_amount(&self, client_id: &str, amount: f64) -> AppResult<WriteOffResult> {
        let timestamp = Utc::now();
        
        let result = sqlx::query!(
            r#"
            INSERT INTO accounts (client_id, amount, locked, updated_at)
            VALUES ($1, $2, 0, $3)
            ON CONFLICT (client_id) DO UPDATE SET
                amount = accounts.amount + $2,
                updated_at = $3
            RETURNING amount
            "#,
            client_id,
            amount,
            timestamp
        )
        .fetch_one(&self.pool)
        .await?;

        Ok(WriteOffResult {
            balance: result.amount,
            timestamp,
        })
    }
}

#[async_trait]
impl CrudStorage<Account, String> for AccountStorage {
    async fn save(&self, entity: Account) -> AppResult<Account> {
        sqlx::query!(
            r#"
            INSERT INTO accounts (client_id, amount, locked, updated_at)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (client_id) DO UPDATE SET
                amount = $2, locked = $3, updated_at = $4
            "#,
            entity.client_id,
            entity.amount,
            entity.locked,
            entity.updated_at
        )
        .execute(&self.pool)
        .await?;

        Ok(entity)
    }

    async fn get_by_id(&self, id: String) -> AppResult<Account> {
        let account = sqlx::query_as!(
            Account,
            "SELECT client_id, amount, locked, updated_at FROM accounts WHERE client_id = $1",
            id
        )
        .fetch_optional(&self.pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Account with id {} not found", id)))?;

        Ok(account)
    }

    async fn find_all(&self) -> AppResult<Vec<Account>> {
        let accounts = sqlx::query_as!(
            Account,
            "SELECT client_id, amount, locked, updated_at FROM accounts ORDER BY client_id"
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(accounts)
    }

    async fn delete_by_id(&self, id: String) -> AppResult<()> {
        let result = sqlx::query!("DELETE FROM accounts WHERE client_id = $1", id)
            .execute(&self.pool)
            .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::NotFound(format!("Account with id {} not found", id)));
        }

        Ok(())
    }
}