use sqlx::{Postgres, Transaction};
use std::collections::HashMap;
use tracing::{debug, error, info, warn};
use uuid::Uuid;

use crate::error::AppError;

/// Manages prepared transactions for two-phase commit protocol
#[derive(Clone)]
pub struct PreparedTransactionManager {
    // In a real implementation, this would be backed by a database table
    // For simplicity, we'll use an in-memory store here
    transactions: std::sync::Arc<tokio::sync::RwLock<HashMap<String, PreparedTransactionState>>>,
}

#[derive(Debug, Clone)]
pub struct PreparedTransactionState {
    pub id: String,
    pub status: TransactionStatus,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum TransactionStatus {
    Prepared,
    Committed,
    RolledBack,
}

impl PreparedTransactionManager {
    pub fn new() -> Self {
        Self {
            transactions: std::sync::Arc::new(tokio::sync::RwLock::new(HashMap::new())),
        }
    }

    /// Prepare a transaction for two-phase commit
    pub async fn prepare_transaction(
        &self,
        transaction_id: &str,
        tx: &mut Transaction<'_, Postgres>,
    ) -> Result<(), AppError> {
        info!("Preparing transaction: {}", transaction_id);

        // Execute PREPARE TRANSACTION in PostgreSQL
        let prepare_sql = format!("PREPARE TRANSACTION '{}'", transaction_id);
        sqlx::query(&prepare_sql)
            .execute(&mut **tx)
            .await
            .map_err(|e| {
                error!("Failed to prepare transaction {}: {}", transaction_id, e);
                AppError::DatabaseError(e.to_string())
            })?;

        // Store in our local state
        let state = PreparedTransactionState {
            id: transaction_id.to_string(),
            status: TransactionStatus::Prepared,
            created_at: chrono::Utc::now(),
        };

        self.transactions
            .write()
            .await
            .insert(transaction_id.to_string(), state);

        debug!("Transaction {} prepared successfully", transaction_id);
        Ok(())
    }

    /// Commit a prepared transaction
    pub async fn commit_transaction(
        &self,
        transaction_id: &str,
        pool: &sqlx::PgPool,
    ) -> Result<(), AppError> {
        info!("Committing prepared transaction: {}", transaction_id);

        // Check if transaction exists and is prepared
        {
            let transactions = self.transactions.read().await;
            match transactions.get(transaction_id) {
                Some(state) if state.status != TransactionStatus::Prepared => {
                    warn!("Transaction {} is not in prepared state: {:?}", transaction_id, state.status);
                    return Err(AppError::InvalidStatus(format!(
                        "Transaction {} is not prepared", 
                        transaction_id
                    )));
                }
                None => {
                    warn!("Prepared transaction {} not found, might already be committed", transaction_id);
                    // Continue with commit attempt - PostgreSQL will handle if it doesn't exist
                }
                _ => {} // Transaction exists and is prepared
            }
        }

        // Execute COMMIT PREPARED in PostgreSQL
        let commit_sql = format!("COMMIT PREPARED '{}'", transaction_id);
        sqlx::query(&commit_sql)
            .execute(pool)
            .await
            .map_err(|e| {
                error!("Failed to commit prepared transaction {}: {}", transaction_id, e);
                AppError::DatabaseError(e.to_string())
            })?;

        // Update local state
        if let Some(mut state) = self.transactions.write().await.get_mut(transaction_id) {
            state.status = TransactionStatus::Committed;
        }

        info!("Transaction {} committed successfully", transaction_id);
        Ok(())
    }

    /// Rollback a prepared transaction
    pub async fn rollback_transaction(
        &self,
        transaction_id: &str,
        pool: &sqlx::PgPool,
    ) -> Result<(), AppError> {
        info!("Rolling back prepared transaction: {}", transaction_id);

        // Check if transaction exists
        {
            let transactions = self.transactions.read().await;
            if let Some(state) = transactions.get(transaction_id) {
                if state.status == TransactionStatus::Committed {
                    warn!("Cannot rollback committed transaction: {}", transaction_id);
                    return Err(AppError::InvalidStatus(format!(
                        "Transaction {} is already committed", 
                        transaction_id
                    )));
                }
            }
        }

        // Execute ROLLBACK PREPARED in PostgreSQL
        let rollback_sql = format!("ROLLBACK PREPARED '{}'", transaction_id);
        sqlx::query(&rollback_sql)
            .execute(pool)
            .await
            .map_err(|e| {
                error!("Failed to rollback prepared transaction {}: {}", transaction_id, e);
                AppError::DatabaseError(e.to_string())
            })?;

        // Update local state
        if let Some(mut state) = self.transactions.write().await.get_mut(transaction_id) {
            state.status = TransactionStatus::RolledBack;
        }

        info!("Transaction {} rolled back successfully", transaction_id);
        Ok(())
    }

    /// List all active (prepared) transactions
    pub async fn list_active_transactions(&self) -> Result<Vec<String>, AppError> {
        let transactions = self.transactions.read().await;
        let active: Vec<String> = transactions
            .values()
            .filter(|state| state.status == TransactionStatus::Prepared)
            .map(|state| state.id.clone())
            .collect();
        
        Ok(active)
    }

    /// Generate a new transaction ID
    pub fn generate_transaction_id() -> String {
        Uuid::new_v4().to_string()
    }
}

impl Default for PreparedTransactionManager {
    fn default() -> Self {
        Self::new()
    }
}

/// Helper function to execute code within a prepared transaction context
pub async fn with_prepared_transaction<F, R>(
    pool: &sqlx::PgPool,
    transaction_id: Option<&str>,
    manager: &PreparedTransactionManager,
    operation: F,
) -> Result<R, AppError>
where
    F: FnOnce(&mut Transaction<'_, Postgres>) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<R, AppError>> + Send + '_>>,
{
    let mut tx = pool.begin().await.map_err(|e| AppError::DatabaseError(e.to_string()))?;
    
    let result = operation(&mut tx).await;
    
    match result {
        Ok(value) => {
            if let Some(txn_id) = transaction_id {
                // Prepare the transaction instead of committing
                manager.prepare_transaction(txn_id, &mut tx).await?;
            } else {
                // Regular commit
                tx.commit().await.map_err(|e| AppError::DatabaseError(e.to_string()))?;
            }
            Ok(value)
        }
        Err(e) => {
            let _ = tx.rollback().await;
            Err(e)
        }
    }
}