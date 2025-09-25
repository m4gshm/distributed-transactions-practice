mod service;

use tonic::transport::Server;
use tracing_subscriber;
use common::tpc::v1::two_phase_commit_service_server::TwoPhaseCommitServiceServer;
use common::database::Database;
use common::utils::ServiceConfig;
use service::TwoPhaseCommitServiceImpl;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::fmt::init();

    let config = ServiceConfig::from_env();
    
    // Initialize database - TPC service needs direct access to PostgreSQL for prepared transactions
    let database = Database::new(&config.database_url).await?;

    // Create TPC service implementation
    let tpc_service = TwoPhaseCommitServiceImpl::new(database.pool().clone());

    let addr = config.bind_address().parse()?;
    tracing::info!("Two-Phase Commit service listening on {}", addr);

    // Start gRPC server
    Server::builder()
        .add_service(TwoPhaseCommitServiceServer::new(tpc_service))
        .serve(addr)
        .await?;

    Ok(())
}