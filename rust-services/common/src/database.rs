use sqlx::{PgPool, Postgres, Transaction};
use std::sync::Arc;
use crate::error::AppResult;

/// Database connection pool wrapper
#[derive(Clone)]
pub struct Database {
    pool: Arc<PgPool>,
}

impl Database {
    pub async fn new(database_url: &str) -> AppResult<Self> {
        let pool = PgPool::connect(database_url).await?;
        Ok(Self {
            pool: Arc::new(pool),
        })
    }

    pub fn pool(&self) -> &PgPool {
        &self.pool
    }

    /// Begin a new transaction (mirrors Java jooq.inTransaction pattern)
    pub async fn begin_transaction(&self) -> AppResult<Transaction<'_, Postgres>> {
        Ok(self.pool.begin().await?)
    }
}

/// Trait for database storage operations (mirrors Java storage interfaces)
#[async_trait::async_trait]
pub trait CrudStorage<T, ID> {
    async fn save(&self, entity: T) -> AppResult<T>;
    async fn get_by_id(&self, id: ID) -> AppResult<T>;
    async fn find_all(&self) -> AppResult<Vec<T>>;
    async fn delete_by_id(&self, id: ID) -> AppResult<()>;
}

/// Transaction context for two-phase commit operations
pub struct TransactionContext<'a> {
    pub tx: &'a mut Transaction<'static, Postgres>,
    pub prepa                 red_transaction_id: Option<String>,
}

impl<'a> TransactionContext<'a> {
    pub fn new(
        tx: &'a mut Transaction<'static, Postgres>,
        prepared_transaction_id: Option<String>,
    ) -> Self {
        Self {
            tx,
            prepared_transaction_id,
        }
    }

    /// Prepare a transaction for two-phase commit if transaction ID is provided
    pub async fn prepare_if_needed(&mut self) -> AppResult<()> {
        if let Some(ref tx_id) = self.prepared_transaction_id {
            sqlx::query(&format!("PREPARE TRANSACTION '{}'", tx_id))
                .execute(&mut **self.tx)
                .await?;
        }
        Ok(())
    }
}
