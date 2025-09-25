use async_trait::async_trait;
use sqlx::{PgPool, Postgres, Transaction};
use common::database::CrudStorage;
use common::error::{AppError, AppResult};
use crate::models::{Order, OrderItem};

/// Order storage implementation (mirrors Java OrderStorage)
pub struct OrderStorage {
    pool: PgPool,
}

impl OrderStorage {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    pub async fn save_with_items(&self, order: &Order, items: &[OrderItem]) -> AppResult<Order> {
        let mut tx = self.pool.begin().await?;
        
        // Save order
        sqlx::query!(
            r#"
            INSERT INTO orders (
                id, created_at, updated_at, customer_id, payment_id, reserve_id,
                status, payment_status, delivery_address, delivery_type, delivery_date_time,
                payment_transaction_id, reserve_transaction_id
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
            ON CONFLICT (id) DO UPDATE SET
                updated_at = $3, payment_id = $5, reserve_id = $6, status = $7,
                payment_status = $8, payment_transaction_id = $12, reserve_transaction_id = $13
            "#,
            order.id,
            order.created_at,
            order.updated_at,
            order.customer_id,
            order.payment_id,
            order.reserve_id,
            order.status as _,
            order.payment_status.map(|s| s as _),
            order.delivery_address,
            order.delivery_type as _,
            order.delivery_date_time,
            order.payment_transaction_id,
            order.reserve_transaction_id
        )
        .execute(&mut *tx)
        .await?;

        // Delete existing items and insert new ones
        sqlx::query!("DELETE FROM order_items WHERE order_id = $1", order.id)
            .execute(&mut *tx)
            .await?;

        for item in items {
            sqlx::query!(
                "INSERT INTO order_items (id, order_id, item_id, amount, insufficient, reserved) VALUES ($1, $2, $3, $4, $5, $6)",
                item.id,
                item.order_id,
                item.item_id,
                item.amount,
                item.insufficient,
                item.reserved
            )
            .execute(&mut *tx)
            .await?;
        }

        tx.commit().await?;
        Ok(order.clone())
    }

    pub async fn get_with_items(&self, id: &str) -> AppResult<(Order, Vec<OrderItem>)> {
        let order = self.get_by_id(id.to_string()).await?;
        
        let items = sqlx::query_as!(
            OrderItem,
            "SELECT * FROM order_items WHERE order_id = $1 ORDER BY item_id",
            id
        )
        .fetch_all(&self.pool)
        .await?;

        Ok((order, items))
    }
}

#[async_trait]
impl CrudStorage<Order, String> for OrderStorage {
    async fn save(&self, entity: Order) -> AppResult<Order> {
        sqlx::query!(
            r#"
            INSERT INTO orders (
                id, created_at, updated_at, customer_id, payment_id, reserve_id,
                status, payment_status, delivery_address, delivery_type, delivery_date_time,
                payment_transaction_id, reserve_transaction_id
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
            ON CONFLICT (id) DO UPDATE SET
                updated_at = $3, payment_id = $5, reserve_id = $6, status = $7,
                payment_status = $8, payment_transaction_id = $12, reserve_transaction_id = $13
            "#,
            entity.id,
            entity.created_at,
            entity.updated_at,
            entity.customer_id,
            entity.payment_id,
            entity.reserve_id,
            entity.status as _,
            entity.payment_status.map(|s| s as _),
            entity.delivery_address,
            entity.delivery_type as _,
            entity.delivery_date_time,
            entity.payment_transaction_id,
            entity.reserve_transaction_id
        )
        .execute(&self.pool)
        .await?;

        Ok(entity)
    }

    async fn get_by_id(&self, id: String) -> AppResult<Order> {
        let order = sqlx::query_as!(
            Order,
            r#"
            SELECT 
                id, created_at, updated_at, customer_id, payment_id, reserve_id,
                status as "status: _", payment_status as "payment_status: _",
                delivery_address, delivery_type as "delivery_type: _", delivery_date_time,
                payment_transaction_id, reserve_transaction_id
            FROM orders WHERE id = $1"#,
            id
        )
        .fetch_optional(&self.pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Order with id {} not found", id)))?;

        Ok(order)
    }

    async fn find_all(&self) -> AppResult<Vec<Order>> {
        let orders = sqlx::query_as!(
            Order,
            r#"
            SELECT 
                id, created_at, updated_at, customer_id, payment_id, reserve_id,
                status as "status: _", payment_status as "payment_status: _",
                delivery_address, delivery_type as "delivery_type: _", delivery_date_time,
                payment_transaction_id, reserve_transaction_id
            FROM orders ORDER BY created_at DESC"#
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(orders)
    }

    async fn delete_by_id(&self, id: String) -> AppResult<()> {
        let result = sqlx::query!("DELETE FROM orders WHERE id = $1", id)
            .execute(&self.pool)
            .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::NotFound(format!("Order with id {} not found", id)));
        }

        Ok(())
    }
}