use chrono::{DateTime, Utc};
use prost_types::Timestamp;
use uuid::Uuid;

/// Utility functions for common conversions and operations

/// Convert Chrono DateTime to protobuf Timestamp
pub fn datetime_to_timestamp(dt: DateTime<Utc>) -> Option<Timestamp> {
    Some(Timestamp {
        seconds: dt.timestamp(),
        nanos: dt.timestamp_subsec_nanos() as i32,
    })
}

/// Convert protobuf Timestamp to Chrono DateTime
pub fn timestamp_to_datetime(ts: &Timestamp) -> DateTime<Utc> {
    DateTime::from_timestamp(ts.seconds, ts.nanos as u32).unwrap_or_default()
}

/// Generate UUID string (mirrors Java UUID.randomUUID().toString())
pub fn generate_uuid() -> String {
    Uuid::new_v4().to_string()
}

/// Validate UUID string format
pub fn is_valid_uuid(s: &str) -> bool {
    Uuid::parse_str(s).is_ok()
}

/// Extract optional field from protobuf message (mirrors Java getOrNull pattern)
pub fn get_optional_field<T, F, R>(msg: &T, has_field: F, get_field: fn(&T) -> R) -> Option<R>
where
    F: Fn(&T) -> bool,
{
    if has_field(msg) {
        Some(get_field(msg))
    } else {
        None
    }
}

/// Logging utility for reactive operations (mirrors Java LogUtils.log)
pub fn log_operation<T>(category: &str, operation_name: &str, result: &Result<T, crate::error::AppError>) {
    match result {
        Ok(_) => tracing::info!("[{}] {} completed successfully", category, operation_name),
        Err(e) => tracing::error!("[{}] {} failed: {}", category, operation_name, e),
    }
}

/// Configuration helper for service ports and addresses
pub struct ServiceConfig {
    pub port: u16,
    pub host: String,
    pub database_url: String,
}

impl Default for ServiceConfig {
    fn default() -> Self {
        Self {
            port: 50051,
            host: "0.0.0.0".to_string(),
            database_url: "postgresql://postgres:postgres@localhost:5432/postgres".to_string(),
        }
    }
}

impl ServiceConfig {
    pub fn from_env() -> Self {
        Self {
            port: std::env::var("SERVER_PORT")
                .unwrap_or_else(|_| "50051".to_string())
                .parse()
                .unwrap_or(50051),
            host: std::env::var("SERVER_HOST").unwrap_or_else(|_| "0.0.0.0".to_string()),
            database_url: std::env::var("DATABASE_URL")
                .unwrap_or_else(|_| "postgresql://postgres:postgres@localhost:5432/postgres".to_string()),
        }
    }

    pub fn bind_address(&self) -> String {
        format!("{}:{}", self.host, self.port)
    }
}