use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use common::status::{OrderStatus, PaymentStatus};

/// Order domain model matching the Java implementation
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Order {
    pub id: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub customer_id: String,
    pub payment_id: Option<String>,
    pub reserve_id: Option<String>,
    pub status: OrderStatus,
    pub payment_status: Option<PaymentStatus>,
    pub delivery_address: String,
    pub delivery_type: DeliveryType,
    pub delivery_date_time: Option<DateTime<Utc>>,
    pub payment_transaction_id: Option<String>,
    pub reserve_transaction_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::Type)]
#[sqlx(type_name = "delivery_type", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DeliveryType {
    Pickup,
    Courier,
}

impl From<common::orders::v1::order::delivery::Type> for DeliveryType {
    fn from(delivery_type: common::orders::v1::order::delivery::Type) -> Self {
        match delivery_type {
            common::orders::v1::order::delivery::Type::Pickup => DeliveryType::Pickup,
            common::orders::v1::order::delivery::Type::Courier => DeliveryType::Courier,
        }
    }
}

impl From<DeliveryType> for common::orders::v1::order::delivery::Type {
    fn from(delivery_type: DeliveryType) -> Self {
        match delivery_type {
            DeliveryType::Pickup => common::orders::v1::order::delivery::Type::Pickup,
            DeliveryType::Courier => common::orders::v1::order::delivery::Type::Courier,
        }
    }
}

/// Order item model
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct OrderItem {
    pub id: String,
    pub order_id: String,
    pub item_id: String,
    pub amount: i32,
    pub insufficient: Option<i32>,
    pub reserved: bool,
}

impl Order {
    pub fn new(
        customer_id: String,
        delivery_address: String,
        delivery_type: DeliveryType,
        delivery_date_time: Option<DateTime<Utc>>,
    ) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4().to_string(),
            created_at: now,
            updated_at: now,
            customer_id,
            payment_id: None,
            reserve_id: None,
            status: OrderStatus::Creating,
            payment_status: None,
            delivery_address,
            delivery_type,
            delivery_date_time,
            payment_transaction_id: None,
            reserve_transaction_id: None,
        }
    }

    pub fn with_status(mut self, status: OrderStatus) -> Self {
        self.status = status;
        self.updated_at = Utc::now();
        self
    }

    pub fn with_payment_id(mut self, payment_id: String) -> Self {
        self.payment_id = Some(payment_id);
        self.updated_at = Utc::now();
        self
    }

    pub fn with_reserve_id(mut self, reserve_id: String) -> Self {
        self.reserve_id = Some(reserve_id);
        self.updated_at = Utc::now();
        self
    }
}