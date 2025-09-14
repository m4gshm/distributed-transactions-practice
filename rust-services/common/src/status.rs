/// Status conversion utilities to match the Java implementation patterns

/// Order status enum matching the Java implementation
#[derive(Debug, Clone, Copy, PartialEq, Eq, sqlx::Type)]
#[sqlx(type_name = "order_status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum OrderStatus {
    Creating,
    Created,
    Approving,
    Approved,
    Releasing,
    Released,
    Insufficient,
    Cancelling,
    Cancelled,
}

impl From<OrderStatus> for crate::orders::v1::order::Status {
    fn from(status: OrderStatus) -> Self {
        match status {
            OrderStatus::Creating => crate::orders::v1::order::Status::Creating,
            OrderStatus::Created => crate::orders::v1::order::Status::Created,
            OrderStatus::Approving => crate::orders::v1::order::Status::Approving,
            OrderStatus::Approved => crate::orders::v1::order::Status::Approved,
            OrderStatus::Releasing => crate::orders::v1::order::Status::Releasing,
            OrderStatus::Released => crate::orders::v1::order::Status::Released,
            OrderStatus::Insufficient => crate::orders::v1::order::Status::Insufficient,
            OrderStatus::Cancelling => crate::orders::v1::order::Status::Cancelling,
            OrderStatus::Cancelled => crate::orders::v1::order::Status::Cancelled,
        }
    }
}

impl From<crate::orders::v1::order::Status> for OrderStatus {
    fn from(status: crate::orders::v1::order::Status) -> Self {
        match status {
            crate::orders::v1::order::Status::Creating => OrderStatus::Creating,
            crate::orders::v1::order::Status::Created => OrderStatus::Created,
            crate::orders::v1::order::Status::Approving => OrderStatus::Approving,
            crate::orders::v1::order::Status::Approved => OrderStatus::Approved,
            crate::orders::v1::order::Status::Releasing => OrderStatus::Releasing,
            crate::orders::v1::order::Status::Released => OrderStatus::Released,
            crate::orders::v1::order::Status::Insufficient => OrderStatus::Insufficient,
            crate::orders::v1::order::Status::Cancelling => OrderStatus::Cancelling,
            crate::orders::v1::order::Status::Cancelled => OrderStatus::Cancelled,
        }
    }
}

/// Payment status enum matching the Java implementation
#[derive(Debug, Clone, Copy, PartialEq, Eq, sqlx::Type)]
#[sqlx(type_name = "payment_status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum PaymentStatus {
    Created,
    Hold,
    Insufficient,
    Paid,
    Cancelled,
}

impl From<PaymentStatus> for crate::payment::v1::payment::Status {
    fn from(status: PaymentStatus) -> Self {
        match status {
            PaymentStatus::Created => crate::payment::v1::payment::Status::Created,
            PaymentStatus::Hold => crate::payment::v1::payment::Status::Hold,
            PaymentStatus::Insufficient => crate::payment::v1::payment::Status::Insufficient,
            PaymentStatus::Paid => crate::payment::v1::payment::Status::Paid,
            PaymentStatus::Cancelled => crate::payment::v1::payment::Status::Cancelled,
        }
    }
}

impl From<crate::payment::v1::payment::Status> for PaymentStatus {
    fn from(status: crate::payment::v1::payment::Status) -> Self {
        match status {
            crate::payment::v1::payment::Status::Created => PaymentStatus::Created,
            crate::payment::v1::payment::Status::Hold => PaymentStatus::Hold,
            crate::payment::v1::payment::Status::Insufficient => PaymentStatus::Insufficient,
            crate::payment::v1::payment::Status::Paid => PaymentStatus::Paid,
            crate::payment::v1::payment::Status::Cancelled => PaymentStatus::Cancelled,
        }
    }
}

/// Reserve status enum matching the Java implementation
#[derive(Debug, Clone, Copy, PartialEq, Eq, sqlx::Type)]
#[sqlx(type_name = "reserve_status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ReserveStatus {
    Created,
    Insufficient,
    Approved,
    Released,
    Cancelled,
}

impl From<ReserveStatus> for crate::reserve::v1::reserve::Status {
    fn from(status: ReserveStatus) -> Self {
        match status {
            ReserveStatus::Created => crate::reserve::v1::reserve::Status::Created,
            ReserveStatus::Insufficient => crate::reserve::v1::reserve::Status::Insufficient,
            ReserveStatus::Approved => crate::reserve::v1::reserve::Status::Approved,
            ReserveStatus::Released => crate::reserve::v1::reserve::Status::Released,
            ReserveStatus::Cancelled => crate::reserve::v1::reserve::Status::Cancelled,
        }
    }
}

impl From<crate::reserve::v1::reserve::Status> for ReserveStatus {
    fn from(status: crate::reserve::v1::reserve::Status) -> Self {
        match status {
            crate::reserve::v1::reserve::Status::Created => ReserveStatus::Created,
            crate::reserve::v1::reserve::Status::Insufficient => ReserveStatus::Insufficient,
            crate::reserve::v1::reserve::Status::Approved => ReserveStatus::Approved,
            crate::reserve::v1::reserve::Status::Released => ReserveStatus::Released,
            crate::reserve::v1::reserve::Status::Cancelled => ReserveStatus::Cancelled,
        }
    }
}