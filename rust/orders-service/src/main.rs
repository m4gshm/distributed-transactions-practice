mod models;
mod storage;
mod service;

use std::sync::Arc;
use tonic::transport::Server;
use tracing_subscriber;
use common::orders::v1::orders_service_server::OrdersServiceServer;
use common::payment::v1::payment_service_client::PaymentServiceClient;
use common::reserve::v1::reserve_service_client::ReserveServiceClient;
use common::tpc::v1::two_phase_commit_service_client::TwoPhaseCommitServiceClient;
use common::database::Database;
use common::utils::ServiceConfig;
use storage::OrderStorage;
use service::OrdersServiceImpl;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt::init();

    let config = ServiceConfig::from_env();
    
    // Initialize database
    let database = Database::new(&config.database_url).await?;
    let storage = Arc::new(OrderStorage::new(database.pool().clone()));

    // Initialize gRPC clients
    let payment_client = PaymentServiceClient::connect("http://localhost:50052").await?;
    let reserve_client = ReserveServiceClient::connect("http://localhost:50053").await?;
    let payment_tpc_client = TwoPhaseCommitServiceClient::connect("http://localhost:50052").await?;
    let reserve_tpc_client = TwoPhaseCommitServiceClient::connect("http://localhost:50053").await?;

    // Create service implementation
    let orders_service = OrdersServiceImpl::new(
        storage,
        database.pool().clone(),
        payment_client,
        reserve_client,
        payment_tpc_client,
        reserve_tpc_client,
    );

    let addr = config.bind_address().parse()?;
    tracing::info!("Orders service listening on {}", addr);

    // Start gRPC server
    Server::builder()
        .add_service(OrdersServiceServer::new(orders_service))
        .serve(addr)
        .await?;

    Ok(())
}