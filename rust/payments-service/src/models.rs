use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use common::status::PaymentStatus;

/// Payment domain model matching the Java implementation
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Payment {
    pub id: String,
    pub external_ref: String,
    pub client_id: String,
    pub amount: f64,
    pub insufficient: Option<f64>,
    pub status: PaymentStatus,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl Payment {
    pub fn new(external_ref: String, client_id: String, amount: f64) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4().to_string(),
            external_ref,
            client_id,
            amount,
            insufficient: None,
            status: PaymentStatus::Created,
            created_at: now,
            updated_at: now,
        }
    }

    pub fn with_status(mut self, status: PaymentStatus) -> Self {
        self.status = status;
        self.updated_at = Utc::now();
        self
    }

    pub fn with_insufficient(mut self, insufficient: Option<f64>) -> Self {
        self.insufficient = insufficient;
        self.updated_at = Utc::now();
        self
    }
}

/// Account domain model
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Account {
    pub client_id: String,
    pub amount: f64,
    pub locked: f64,
    pub updated_at: DateTime<Utc>,
}

impl Account {
    pub fn new(client_id: String, initial_amount: f64) -> Self {
        Self {
            client_id,
            amount: initial_amount,
            locked: 0.0,
            updated_at: Utc::now(),
        }
    }

    pub fn available_balance(&self) -> f64 {
        self.amount - self.locked
    }
}

/// Account operation results
#[derive(Debug)]
pub struct LockResult {
    pub success: bool,
    pub insufficient_amount: f64,
}

#[derive(Debug)]
pub struct WriteOffResult {
    pub balance: f64,
    pub timestamp: DateTime<Utc>,
}