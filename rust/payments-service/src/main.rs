mod models;
mod storage;
mod service;
mod events;

use std::sync::Arc;
use tonic::transport::Server;
use tracing_subscriber;
use common::payment::v1::{
    payment_service_server::PaymentServiceServer,
    account_service_server::AccountServiceServer,
};
use common::tpc::v1::two_phase_commit_service_server::TwoPhaseCommitServiceServer;
use common::database::Database;
use common::utils::ServiceConfig;
use common::transaction::TwoPhaseTransactionManager;
use storage::{PaymentStorage, AccountStorage};
use service::{PaymentServiceImpl, AccountServiceImpl};
use events::AccountEventService;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt::init();

    let config = ServiceConfig::from_env();
    
    // Initialize database
    let database = Database::new(&config.database_url).await?;
    let payment_storage = Arc::new(PaymentStorage::new(database.pool().clone()));
    let account_storage = Arc::new(AccountStorage::new(database.pool().clone()));

    // Initialize Kafka event service  
    let kafka_broker = std::env::var("KAFKA_BROKER").unwrap_or_else(|_| "localhost:9092".to_string());
    let event_service = AccountEventService::new(&kafka_broker, "account-balance-events".to_string())?;

    // Create service implementations
    let payment_service = PaymentServiceImpl::new(
        payment_storage.clone(),
        account_storage.clone(),
        database.pool().clone(),
    );

    let account_service = AccountServiceImpl::new(
        account_storage.clone(),
        event_service,
    );

    // Create TPC service for payment transactions
    let tpc_manager = TwoPhaseTransactionManager::new();

    let addr = config.bind_address().parse()?;
    tracing::info!("Payments service listening on {}", addr);

    // Start gRPC server
    Server::builder()
        .add_service(PaymentServiceServer::new(payment_service))
        .add_service(AccountServiceServer::new(account_service))
        .serve(addr)
        .await?;

    Ok(())
}