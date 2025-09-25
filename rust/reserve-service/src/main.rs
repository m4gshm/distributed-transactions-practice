mod models;
mod storage;
mod service;

use std::sync::Arc;
use tonic::transport::Server;
use tracing_subscriber;
use common::reserve::v1::reserve_service_server::ReserveServiceServer;
use common::warehouse::v1::warehouse_item_service_server::WarehouseItemServiceServer;
use common::database::Database;
use common::utils::ServiceConfig;
use storage::{ReserveStorage, WarehouseItemStorage};
use service::{ReserveServiceImpl, WarehouseItemServiceImpl};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt::init();

    let config = ServiceConfig::from_env();
    
    // Initialize database
    let database = Database::new(&config.database_url).await?;
    let reserve_storage = Arc::new(ReserveStorage::new(database.pool().clone()));
    let warehouse_storage = Arc::new(WarehouseItemStorage::new(database.pool().clone()));

    // Create service implementations
    let reserve_service = ReserveServiceImpl::new(
        reserve_storage.clone(),
        warehouse_storage.clone(),
        database.pool().clone(),
    );

    let warehouse_service = WarehouseItemServiceImpl::new(warehouse_storage.clone());

    let addr = config.bind_address().parse()?;
    tracing::info!("Reserve service listening on {}", addr);

    // Start gRPC server
    Server::builder()
        .add_service(ReserveServiceServer::new(reserve_service))
        .add_service(WarehouseItemServiceServer::new(warehouse_service))
        .serve(addr)
        .await?;

    Ok(())
}