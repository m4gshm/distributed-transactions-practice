use std::time::Duration;
use tonic::transport::{Channel, Endpoint};
use tonic::{Request, Response, Status};
use tracing::{error, info};

/// Configuration for gRPC clients
#[derive(Clone, Debug)]
pub struct ClientConfig {
    pub endpoint: String,
    pub timeout: Duration,
    pub connect_timeout: Duration,
}

impl Default for ClientConfig {
    fn default() -> Self {
        Self {
            endpoint: "http://localhost:50051".to_string(),
            timeout: Duration::from_secs(30),
            connect_timeout: Duration::from_secs(10),
        }
    }
}

impl ClientConfig {
    pub fn new(endpoint: String) -> Self {
        Self {
            endpoint,
            ..Default::default()
        }
    }

    pub fn with_timeout(mut self, timeout: Duration) -> Self {
        self.timeout = timeout;
        self
    }

    pub fn with_connect_timeout(mut self, connect_timeout: Duration) -> Self {
        self.connect_timeout = connect_timeout;
        self
    }
}

/// Utility for creating gRPC channels with proper configuration
pub struct GrpcChannelFactory;

impl GrpcChannelFactory {
    pub async fn create_channel(config: &ClientConfig) -> Result<Channel, Box<dyn std::error::Error + Send + Sync>> {
        let endpoint = Endpoint::from_shared(config.endpoint.clone())?
            .timeout(config.timeout)
            .connect_timeout(config.connect_timeout);

        let channel = endpoint.connect().await?;
        info!("Successfully connected to gRPC endpoint: {}", config.endpoint);
        Ok(channel)
    }
}

/// Enhanced request/response wrapper for gRPC calls with observability
pub struct GrpcRequestWrapper;

impl GrpcRequestWrapper {
    pub fn wrap_request<T>(request: T, operation: &str) -> Request<T> {
        let mut req = Request::new(request);
        
        // Add metadata for tracing
        req.metadata_mut().insert(
            "operation",
            operation.parse().unwrap_or_else(|_| "unknown".parse().unwrap()),
        );
        
        req
    }

    pub fn handle_response<T>(
        response: Result<Response<T>, Status>,
        operation: &str,
    ) -> Result<T, Status> {
        match response {
            Ok(resp) => {
                info!("gRPC operation '{}' completed successfully", operation);
                Ok(resp.into_inner())
            }
            Err(status) => {
                error!("gRPC operation '{}' failed: {:?}", operation, status);
                Err(status)
            }
        }
    }
}

/// Utility trait for converting gRPC status to our application errors
pub trait StatusConverter {
    fn to_app_error(self) -> crate::error::AppError;
}

impl StatusConverter for Status {
    fn to_app_error(self) -> crate::error::AppError {
        match self.code() {
            tonic::Code::NotFound => crate::error::AppError::NotFound(self.message().to_string()),
            tonic::Code::InvalidArgument => crate::error::AppError::InvalidInput(self.message().to_string()),
            tonic::Code::FailedPrecondition => crate::error::AppError::InvalidStatus(self.message().to_string()),
            tonic::Code::Unavailable => crate::error::AppError::ServiceUnavailable(self.message().to_string()),
            _ => crate::error::AppError::Internal(format!("gRPC error: {}", self.message())),
        }
    }
}