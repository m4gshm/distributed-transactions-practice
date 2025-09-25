use thiserror::Error;
use tonic::{Code, Status};

/// Application-specific error types that mirror the Java implementation
#[derive(Error, Debug)]
pub enum AppError {
    #[error("Entity not found: {0}")]
    NotFound(String),
    
    #[error("Invalid status: {0}")]
    InvalidStatus(String),
    
    #[error("Invalid state: {0}")]
    InvalidState(String),
    
    #[error("Invalid input: {0}")]
    InvalidInput(String),
    
    #[error("Insufficient resources: {0}")]
    InsufficientResources(String),
    
    #[error("Database error: {0}")]
    DatabaseError(String),
    
    #[error("SQL error: {0}")]
    Database(#[from] sqlx::Error),
    
    #[error("Transaction error: {0}")]
    TransactionError(String),
    
    #[error("Validation error: {0}")]
    ValidationError(String),
    
    #[error("External service error: {0}")]
    ExternalService(String),
    
    #[error("Service unavailable: {0}")]
    ServiceUnavailable(String),
    
    #[error("Internal error: {0}")]
    Internal(String),
    
    #[error("gRPC error: {0}")]
    Grpc(#[from] tonic::Status),
}

impl From<AppError> for Status {
    fn from(err: AppError) -> Self {
        match err {
            AppError::NotFound(msg) => Status::not_found(msg),
            AppError::InvalidState(msg) => Status::failed_precondition(msg),
            AppError::InsufficientResources(msg) => Status::failed_precondition(msg),
            AppError::Database(ref db_err) => {
                tracing::error!("Database error: {}", db_err);
                Status::internal("Database operation failed")
            }
            AppError::Transaction(msg) => Status::aborted(msg),
            AppError::Validation(msg) => Status::invalid_argument(msg),
            AppError::ExternalService(msg) => Status::unavailable(msg),
        }
    }
}

/// Result type alias for convenience
pub type AppResult<T> = Result<T, AppError>;

/// Helper function to check entity status (mirrors Java checkStatus function)
pub fn check_status<T>(
    operation: &str,
    current_status: T,
    expected_statuses: &[T],
) -> AppResult<()>
where
    T: PartialEq + std::fmt::Debug,
{
    if expected_statuses.contains(&current_status) {
        Ok(())
    } else {
        Err(AppError::InvalidState(format!(
            "Invalid status for operation '{}': expected {:?}, but was {:?}",
            operation, expected_statuses, current_status
        )))
    }
}