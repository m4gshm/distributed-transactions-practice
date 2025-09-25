use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use common::status::ReserveStatus;

/// Reserve domain model matching the Java implementation
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Reserve {
    pub id: String,
    pub external_ref: String,
    pub status: ReserveStatus,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl Reserve {
    pub fn new(external_ref: String) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4().to_string(),
            external_ref,
            status: ReserveStatus::Created,
            created_at: now,
            updated_at: now,
        }
    }

    pub fn with_status(mut self, status: ReserveStatus) -> Self {
        self.status = status;
        self.updated_at = Utc::now();
        self
    }
}

/// Reserve item model
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ReserveItem {
    pub id: String,
    pub reserve_id: String,
    pub item_id: String,
    pub amount: i32,
    pub insufficient: Option<i32>,
    pub reserved: bool,
}

/// Warehouse item model
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct WarehouseItem {
    pub id: String,
    pub amount: i32,
    pub reserved: i32,
    pub updated_at: DateTime<Utc>,
}

impl WarehouseItem {
    pub fn available(&self) -> i32 {
        self.amount - self.reserved
    }
}

/// Item operation for batch processing
#[derive(Debug, Clone)]
pub struct ItemOperation {
    pub item_id: String,
    pub amount: i32,
}

/// Result of item reservation operation
#[derive(Debug, Clone)]
pub struct ItemReserveResult {
    pub id: String,
    pub reserved: bool,
    pub remainder: i32, // Remaining amount if reservation failed
}