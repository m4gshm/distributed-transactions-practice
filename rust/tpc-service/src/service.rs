use std::sync::Arc;
use tonic::{Request, Response, Status};
use common::tpc::v1::{
    two_phase_commit_service_server::TwoPhaseCommitService,
    *,
};
use common::error::{AppError, AppResult};
use common::transaction::TwoPhaseTransactionManager;

/// Two-Phase Commit service implementation (mirrors Java TwoPhaseCommitServiceImpl)
pub struct TwoPhaseCommitServiceImpl {
    transaction_manager: Arc<TwoPhaseTransactionManager>,
    pool: sqlx::PgPool,
}

impl TwoPhaseCommitServiceImpl {
    pub fn new(pool: sqlx::PgPool) -> Self {
        Self {
            transaction_manager: Arc::new(TwoPhaseTransactionManager::new()),
            pool,
        }
    }

    /// List all active prepared transactions from PostgreSQL
    async fn get_prepared_transactions(&self) -> AppResult<Vec<String>> {
        let rows = sqlx::query!(
            "SELECT gid FROM pg_prepared_xacts ORDER BY gid"
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(|row| row.gid).collect())
    }

    /// Commit a prepared transaction by ID
    async fn commit_prepared_transaction(&self, transaction_id: &str) -> AppResult<()> {
        tracing::info!("Committing prepared transaction: {}", transaction_id);
        
        // Execute COMMIT PREPARED
        let result = sqlx::query(&format!("COMMIT PREPARED '{}'", transaction_id))
            .execute(&self.pool)
            .await;

        match result {
            Ok(_) => {
                tracing::info!("Successfully committed prepared transaction: {}", transaction_id);
                Ok(())
            }
            Err(sqlx::Error::Database(db_err)) if db_err.message().contains("does not exist") => {
                tracing::warn!("Prepared transaction does not exist: {}", transaction_id);
                // This is not an error - transaction might have been already committed
                Ok(())
            }
            Err(e) => {
                tracing::error!("Failed to commit prepared transaction {}: {}", transaction_id, e);
                Err(AppError::Transaction(format!("Failed to commit transaction {}: {}", transaction_id, e)))
            }
        }
    }

    /// Rollback a prepared transaction by ID
    async fn rollback_prepared_transaction(&self, transaction_id: &str) -> AppResult<()> {
        tracing::info!("Rolling back prepared transaction: {}", transaction_id);
        
        // Execute ROLLBACK PREPARED
        let result = sqlx::query(&format!("ROLLBACK PREPARED '{}'", transaction_id))
            .execute(&self.pool)
            .await;

        match result {
            Ok(_) => {
                tracing::info!("Successfully rolled back prepared transaction: {}", transaction_id);
                Ok(())
            }
            Err(sqlx::Error::Database(db_err)) if db_err.message().contains("does not exist") => {
                tracing::warn!("Prepared transaction does not exist: {}", transaction_id);
                // This is not an error - transaction might have been already rolled back
                Ok(())
            }
            Err(e) => {
                tracing::error!("Failed to rollback prepared transaction {}: {}", transaction_id, e);
                Err(AppError::Transaction(format!("Failed to rollback transaction {}: {}", transaction_id, e)))
            }
        }
    }
}

#[tonic::async_trait]
impl TwoPhaseCommitService for TwoPhaseCommitServiceImpl {
    async fn commit(
        &self,
        request: Request<TwoPhaseCommitRequest>,
    ) -> Result<Response<TwoPhaseCommitResponse>, Status> {
        let req = request.into_inner();
        
        match self.commit_prepared_transaction(&req.id).await {
            Ok(()) => Ok(Response::new(TwoPhaseCommitResponse {
                message: format!("Transaction {} committed successfully", req.id),
                id: req.id,
            })),
            Err(e) => {
                tracing::error!("Error committing transaction {}: {}", req.id, e);
                Err(e.into())
            }
        }
    }

    async fn rollback(
        &self,
        request: Request<TwoPhaseRollbackRequest>,
    ) -> Result<Response<TwoPhaseRollbackResponse>, Status> {
        let req = request.into_inner();
        
        match self.rollback_prepared_transaction(&req.id).await {
            Ok(()) => Ok(Response::new(TwoPhaseRollbackResponse {
                message: format!("Transaction {} rolled back successfully", req.id),
                id: req.id,
            })),
            Err(e) => {
                tracing::error!("Error rolling back transaction {}: {}", req.id, e);
                Err(e.into())
            }
        }
    }

    async fn list_actives(
        &self,
        _request: Request<TwoPhaseListActivesRequest>,
    ) -> Result<Response<TwoPhaseListActivesResponse>, Status> {
        match self.get_prepared_transactions().await {
            Ok(transaction_ids) => {
                let transactions: Vec<two_phase_list_actives_response::Transaction> = 
                    transaction_ids.into_iter().map(|id| {
                        two_phase_list_actives_response::Transaction { id }
                    }).collect();

                tracing::info!("Found {} active prepared transactions", transactions.len());
                
                Ok(Response::new(TwoPhaseListActivesResponse {
                    transactions,
                }))
            }
            Err(e) => {
                tracing::error!("Error listing active transactions: {}", e);
                Err(e.into())
            }
        }
    }
}