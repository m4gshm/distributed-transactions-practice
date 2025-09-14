use async_trait::async_trait;
use sqlx::PgPool;
use common::database::CrudStorage;
use common::error::{AppError, AppResult};
use crate::models::{Reserve, ReserveItem, WarehouseItem, ItemOperation, ItemReserveResult};
use chrono::Utc;

/// Reserve storage implementation (mirrors Java ReserveStorage)
pub struct ReserveStorage {
    pool: PgPool,
}

impl ReserveStorage {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    pub async fn save_with_items(&self, reserve: &Reserve, items: &[ReserveItem]) -> AppResult<Reserve> {
        let mut tx = self.pool.begin().await?;
        
        // Save reserve
        sqlx::query!(
            r#"
            INSERT INTO reserves (id, external_ref, status, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (id) DO UPDATE SET
                status = $3, updated_at = $5
            "#,
            reserve.id,
            reserve.external_ref,
            reserve.status as _,
            reserve.created_at,
            reserve.updated_at
        )
        .execute(&mut *tx)
        .await?;

        // Delete existing items and insert new ones
        sqlx::query!("DELETE FROM reserve_items WHERE reserve_id = $1", reserve.id)
            .execute(&mut *tx)
            .await?;

        for item in items {
            sqlx::query!(
                "INSERT INTO reserve_items (id, reserve_id, item_id, amount, insufficient, reserved) VALUES ($1, $2, $3, $4, $5, $6)",
                item.id,
                item.reserve_id,
                item.item_id,
                item.amount,
                item.insufficient,
                item.reserved
            )
            .execute(&mut *tx)
            .await?;
        }

        tx.commit().await?;
        Ok(reserve.clone())
    }

    pub async fn get_with_items(&self, id: &str) -> AppResult<(Reserve, Vec<ReserveItem>)> {
        let reserve = self.get_by_id(id.to_string()).await?;
        
        let items = sqlx::query_as!(
            ReserveItem,
            "SELECT * FROM reserve_items WHERE reserve_id = $1 ORDER BY item_id",
            id
        )
        .fetch_all(&self.pool)
        .await?;

        Ok((reserve, items))
    }
}

#[async_trait]
impl CrudStorage<Reserve, String> for ReserveStorage {
    async fn save(&self, entity: Reserve) -> AppResult<Reserve> {
        sqlx::query!(
            r#"
            INSERT INTO reserves (id, external_ref, status, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (id) DO UPDATE SET
                status = $3, updated_at = $5
            "#,
            entity.id,
            entity.external_ref,
            entity.status as _,
            entity.created_at,
            entity.updated_at
        )
        .execute(&self.pool)
        .await?;

        Ok(entity)
    }

    async fn get_by_id(&self, id: String) -> AppResult<Reserve> {
        let reserve = sqlx::query_as!(
            Reserve,
            r#"
            SELECT id, external_ref, status as "status: _", created_at, updated_at
            FROM reserves WHERE id = $1
            "#,
            id
        )
        .fetch_optional(&self.pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Reserve with id {} not found", id)))?;

        Ok(reserve)
    }

    async fn find_all(&self) -> AppResult<Vec<Reserve>> {
        let reserves = sqlx::query_as!(
            Reserve,
            r#"
            SELECT id, external_ref, status as "status: _", created_at, updated_at
            FROM reserves ORDER BY created_at DESC
            "#
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(reserves)
    }

    async fn delete_by_id(&self, id: String) -> AppResult<()> {
        let result = sqlx::query!("DELETE FROM reserves WHERE id = $1", id)
            .execute(&self.pool)
            .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::NotFound(format!("Reserve with id {} not found", id)));
        }

        Ok(())
    }
}

/// Warehouse item storage implementation (mirrors Java WarehouseItemStorage)
pub struct WarehouseItemStorage {
    pool: PgPool,
}

impl WarehouseItemStorage {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Reserve items in batch (mirrors Java reserve method)
    pub async fn reserve(&self, operations: Vec<ItemOperation>) -> AppResult<Vec<ItemReserveResult>> {
        let mut tx = self.pool.begin().await?;
        let mut results = Vec::new();

        for op in operations {
            let item = sqlx::query_as!(
                WarehouseItem,
                "SELECT id, amount, reserved, updated_at FROM warehouse_items WHERE id = $1 FOR UPDATE",
                op.item_id
            )
            .fetch_optional(&mut *tx)
            .await?
            .ok_or_else(|| AppError::NotFound(format!("Warehouse item {} not found", op.item_id)))?;

            let available = item.available();
            if available >= op.amount {
                // Sufficient inventory - reserve it
                sqlx::query!(
                    "UPDATE warehouse_items SET reserved = reserved + $1, updated_at = $2 WHERE id = $3",
                    op.amount,
                    Utc::now(),
                    op.item_id
                )
                .execute(&mut *tx)
                .await?;

                results.push(ItemReserveResult {
                    id: op.item_id,
                    reserved: true,
                    remainder: 0,
                });
            } else {
                // Insufficient inventory
                results.push(ItemReserveResult {
                    id: op.item_id,
                    reserved: false,
                    remainder: available,
                });
            }
        }

        tx.commit().await?;
        Ok(results)
    }

    /// Cancel reserve (mirrors Java cancelReserve method)
    pub async fn cancel_reserve(&self, operations: Vec<ItemOperation>) -> AppResult<Vec<String>> {
        let mut tx = self.pool.begin().await?;
        let mut cancelled_items = Vec::new();

        for op in operations {
            sqlx::query!(
                "UPDATE warehouse_items SET reserved = GREATEST(0, reserved - $1), updated_at = $2 WHERE id = $3",
                op.amount,
                Utc::now(),
                op.item_id
            )
            .execute(&mut *tx)
            .await?;

            cancelled_items.push(op.item_id);
        }

        tx.commit().await?;
        Ok(cancelled_items)
    }

    /// Release items (mirrors Java release method)
    pub async fn release(&self, operations: Vec<ItemOperation>) -> AppResult<Vec<String>> {
        let mut tx = self.pool.begin().await?;
        let mut released_items = Vec::new();

        for op in operations {
            // First remove from reserved, then from total amount
            sqlx::query!(
                r#"
                UPDATE warehouse_items 
                SET amount = GREATEST(0, amount - $1),
                    reserved = GREATEST(0, reserved - $1),
                    updated_at = $2 
                WHERE id = $3
                "#,
                op.amount,
                Utc::now(),
                op.item_id
            )
            .execute(&mut *tx)
            .await?;

            released_items.push(op.item_id);
        }

        tx.commit().await?;
        Ok(released_items)
    }

    /// Get item cost (mirrors Java WarehouseItemService.getItemCost)
    pub async fn get_item_cost(&self, item_id: &str) -> AppResult<f64> {
        // Simplified cost calculation - in real implementation this would be more complex
        let item = sqlx::query_as!(
            WarehouseItem,
            "SELECT id, amount, reserved, updated_at FROM warehouse_items WHERE id = $1",
            item_id
        )
        .fetch_optional(&self.pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Warehouse item {} not found", item_id)))?;

        // Simple cost calculation: $10 per item
        Ok(10.0)
    }

    /// Top up item inventory
    pub async fn top_up(&self, item_id: &str, amount: i32) -> AppResult<i32> {
        let timestamp = Utc::now();
        
        let result = sqlx::query!(
            r#"
            INSERT INTO warehouse_items (id, amount, reserved, updated_at)
            VALUES ($1, $2, 0, $3)
            ON CONFLICT (id) DO UPDATE SET
                amount = warehouse_items.amount + $2,
                updated_at = $3
            RETURNING amount
            "#,
            item_id,
            amount,
            timestamp
        )
        .fetch_one(&self.pool)
        .await?;

        Ok(result.amount)
    }
}

#[async_trait]
impl CrudStorage<WarehouseItem, String> for WarehouseItemStorage {
    async fn save(&self, entity: WarehouseItem) -> AppResult<WarehouseItem> {
        sqlx::query!(
            r#"
            INSERT INTO warehouse_items (id, amount, reserved, updated_at)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (id) DO UPDATE SET
                amount = $2, reserved = $3, updated_at = $4
            "#,
            entity.id,
            entity.amount,
            entity.reserved,
            entity.updated_at
        )
        .execute(&self.pool)
        .await?;

        Ok(entity)
    }

    async fn get_by_id(&self, id: String) -> AppResult<WarehouseItem> {
        let item = sqlx::query_as!(
            WarehouseItem,
            "SELECT id, amount, reserved, updated_at FROM warehouse_items WHERE id = $1",
            id
        )
        .fetch_optional(&self.pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Warehouse item with id {} not found", id)))?;

        Ok(item)
    }

    async fn find_all(&self) -> AppResult<Vec<WarehouseItem>> {
        let items = sqlx::query_as!(
            WarehouseItem,
            "SELECT id, amount, reserved, updated_at FROM warehouse_items ORDER BY id"
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(items)
    }

    async fn delete_by_id(&self, id: String) -> AppResult<()> {
        let result = sqlx::query!("DELETE FROM warehouse_items WHERE id = $1", id)
            .execute(&self.pool)
            .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::NotFound(format!("Warehouse item with id {} not found", id)));
        }

        Ok(())
    }
}