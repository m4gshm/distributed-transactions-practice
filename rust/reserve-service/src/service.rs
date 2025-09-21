use std::sync::Arc;
use tonic::{Request, Response, Status};
use common::reserve::v1::{
    reserve_service_server::ReserveService,
    *,
};
use common::warehouse::v1::{
    warehouse_item_service_server::WarehouseItemService,
    *,
};
use common::error::{AppError, AppResult, check_status};
use common::transaction::in_transaction;
use common::utils::{generate_uuid, datetime_to_timestamp};
use common::status::ReserveStatus;
use crate::models::{Reserve, ReserveItem, ItemOperation};
use crate::storage::{ReserveStorage, WarehouseItemStorage};

/// Reserve service implementation (mirrors Java ReserveServiceImpl)
pub struct ReserveServiceImpl {
    storage: Arc<ReserveStorage>,
    warehouse_storage: Arc<WarehouseItemStorage>,
    pool: sqlx::PgPool,
}

impl ReserveServiceImpl {
    pub fn new(
        storage: Arc<ReserveStorage>,
        warehouse_storage: Arc<WarehouseItemStorage>,
        pool: sqlx::PgPool,
    ) -> Self {
        Self {
            storage,
            warehouse_storage,
            pool,
        }
    }

    fn to_item_operations(items: &[ReserveItem]) -> Vec<ItemOperation> {
        items.iter().map(|item| ItemOperation {
            item_id: item.item_id.clone(),
            amount: item.amount,
        }).collect()
    }

    async fn reserve_in_status<F, Fut, T>(
        &self,
        operation_name: &str,
        reserve_id: &str,
        expected_statuses: &[ReserveStatus],
        prepared_transaction_id: Option<String>,
        operation: F,
    ) -> AppResult<T>
    where
        F: FnOnce(Reserve, Vec<ReserveItem>) -> Fut,
        Fut: std::future::Future<Output = AppResult<T>>,
    {
        in_transaction(&self.pool, prepared_transaction_id, |_tx| async {
            let (reserve, items) = self.storage.get_with_items(reserve_id).await?;
            check_status(operation_name, reserve.status, expected_statuses)?;
            operation(reserve, items).await
        }).await
    }
}

#[tonic::async_trait]
impl ReserveService for ReserveServiceImpl {
    async fn create(
        &self,
        request: Request<ReserveCreateRequest>,
    ) -> Result<Response<ReserveCreateResponse>, Status> {
        let req = request.into_inner();
        let body = req.body.ok_or_else(|| Status::invalid_argument("Request body required"))?;
        
        let reserve_id = generate_uuid();
        let reserve = Reserve::new(body.external_ref);
        
        let items: Vec<ReserveItem> = body.items.iter().map(|item| {
            ReserveItem {
                id: generate_uuid(),
                reserve_id: reserve_id.clone(),
                item_id: item.id.clone(),
                amount: item.amount,
                insufficient: None,
                reserved: false,
            }
        }).collect();

        let result = in_transaction(&self.pool, req.prepared_transaction_id, |_tx| async {
            self.storage.save_with_items(&reserve, &items).await?;
            Ok(ReserveCreateResponse {
                id: reserve_id.clone(),
            })
        }).await;

        match result {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn approve(
        &self,
        request: Request<ReserveApproveRequest>,
    ) -> Result<Response<ReserveApproveResponse>, Status> {
        let req = request.into_inner();
        
        let result = self.reserve_in_status(
            "approve",
            &req.id,
            &[ReserveStatus::Created],
            req.prepared_transaction_id,
            |reserve, items| async move {
                let not_reserved_items: Vec<_> = items.iter().filter(|item| !item.reserved).cloned().collect();
                
                if not_reserved_items.is_empty() {
                    return Err(AppError::Validation("All items already reserved".to_string()));
                }

                let operations = Self::to_item_operations(&not_reserved_items);
                let reserve_results = self.warehouse_storage.reserve(operations).await?;

                // Update items based on reservation results
                let mut updated_items = items.clone();
                for result in &reserve_results {
                    if let Some(item) = updated_items.iter_mut().find(|i| i.item_id == result.id) {
                        item.reserved = result.reserved;
                        if !result.reserved {
                            item.insufficient = Some(-result.remainder);
                        }
                    }
                }

                // Check if all items are now reserved
                let all_reserved = updated_items.iter().all(|item| item.reserved);
                let new_status = if all_reserved {
                    ReserveStatus::Approved
                } else {
                    ReserveStatus::Insufficient
                };

                let updated_reserve = reserve.with_status(new_status);
                self.storage.save_with_items(&updated_reserve, &updated_items).await?;

                // Build response
                let response_items: Vec<reserve_approve_response::Item> = reserve_results.iter().map(|result| {
                    reserve_approve_response::Item {
                        id: result.id.clone(),
                        insufficient_quantity: if result.reserved { 0 } else { result.remainder },
                        reserved: result.reserved,
                    }
                }).collect();

                Ok(ReserveApproveResponse {
                    id: req.id.clone(),
                    status: new_status.into(),
                    items: response_items,
                })
            }
        ).await;

        match result {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn cancel(
        &self,
        request: Request<ReserveCancelRequest>,
    ) -> Result<Response<ReserveCancelResponse>, Status> {
        let req = request.into_inner();
        
        let result = self.reserve_in_status(
            "cancel",
            &req.id,
            &[ReserveStatus::Created, ReserveStatus::Approved],
            req.prepared_transaction_id,
            |reserve, items| async move {
                let operations = Self::to_item_operations(&items);
                let _ = self.warehouse_storage.cancel_reserve(operations).await?;

                let updated_reserve = reserve.with_status(ReserveStatus::Cancelled);
                self.storage.save(updated_reserve).await?;

                Ok(ReserveCancelResponse {
                    id: req.id.clone(),
                    status: ReserveStatus::Cancelled.into(),
                })
            }
        ).await;

        match result {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn release(
        &self,
        request: Request<ReserveReleaseRequest>,
    ) -> Result<Response<ReserveReleaseResponse>, Status> {
        let req = request.into_inner();
        
        let result = self.reserve_in_status(
            "release",
            &req.id,
            &[ReserveStatus::Approved],
            req.prepared_transaction_id,
            |reserve, items| async move {
                let operations = Self::to_item_operations(&items);
                let _ = self.warehouse_storage.release(operations).await?;

                let updated_reserve = reserve.with_status(ReserveStatus::Released);
                self.storage.save(updated_reserve).await?;

                Ok(ReserveReleaseResponse {
                    id: req.id.clone(),
                    status: ReserveStatus::Released.into(),
                })
            }
        ).await;

        match result {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn get(
        &self,
        request: Request<ReserveGetRequest>,
    ) -> Result<Response<ReserveGetResponse>, Status> {
        let req = request.into_inner();
        
        match self.storage.get_with_items(&req.id).await {
            Ok((reserve, items)) => {
                let proto_items: Vec<common::reserve::v1::reserve::Item> = items.into_iter().map(|item| {
                    common::reserve::v1::reserve::Item {
                        id: item.item_id,
                        amount: item.amount,
                        insufficient: item.insufficient,
                        reserved: item.reserved,
                    }
                }).collect();

                let proto_reserve = common::reserve::v1::Reserve {
                    id: reserve.id,
                    external_ref: reserve.external_ref,
                    status: reserve.status.into(),
                    items: proto_items,
                };
                
                Ok(Response::new(ReserveGetResponse {
                    reserve: Some(proto_reserve),
                }))
            }
            Err(e) => Err(e.into()),
        }
    }

    async fn list(
        &self,
        _request: Request<ReserveListRequest>,
    ) -> Result<Response<ReserveListResponse>, Status> {
        match self.storage.find_all().await {
            Ok(reserves) => {
                let mut proto_reserves = Vec::new();
                
                for reserve in reserves {
                    // For list operation, we don't load items for performance
                    let proto_reserve = common::reserve::v1::Reserve {
                        id: reserve.id,
                        external_ref: reserve.external_ref,
                        status: reserve.status.into(),
                        items: vec![],
                    };
                    proto_reserves.push(proto_reserve);
                }
                
                Ok(Response::new(ReserveListResponse {
                    reserves: proto_reserves,
                }))
            }
            Err(e) => Err(e.into()),
        }
    }
}

/// Warehouse item service implementation (mirrors Java WarehouseItemServiceImpl)
pub struct WarehouseItemServiceImpl {
    storage: Arc<WarehouseItemStorage>,
}

impl WarehouseItemServiceImpl {
    pub fn new(storage: Arc<WarehouseItemStorage>) -> Self {
        Self { storage }
    }
}

#[tonic::async_trait]
impl WarehouseItemService for WarehouseItemServiceImpl {
    async fn get_item_cost(
        &self,
        request: Request<GetItemCostRequest>,
    ) -> Result<Response<GetItemCostResponse>, Status> {
        let req = request.into_inner();
        
        match self.storage.get_item_cost(&req.id).await {
            Ok(cost) => Ok(Response::new(GetItemCostResponse { cost })),
            Err(e) => Err(e.into()),
        }
    }

    async fn item_list(
        &self,
        _request: Request<ItemListRequest>,
    ) -> Result<Response<ItemListResponse>, Status> {
        match self.storage.find_all().await {
            Ok(items) => {
                let proto_items: Vec<common::warehouse::v1::Item> = items.into_iter().map(|item| {
                    common::warehouse::v1::Item {
                        id: item.id,
                        amount: item.amount,
                        reserved: item.reserved,
                        updated_at: datetime_to_timestamp(item.updated_at),
                    }
                }).collect();
                
                Ok(Response::new(ItemListResponse {
                    accounts: proto_items, // Note: proto field is named 'accounts' but contains items
                }))
            }
            Err(e) => Err(e.into()),
        }
    }

    async fn top_up(
        &self,
        request: Request<ItemTopUpRequest>,
    ) -> Result<Response<ItemTopUpResponse>, Status> {
        let req = request.into_inner();
        let top_up = req.top_up.ok_or_else(|| Status::invalid_argument("TopUp data required"))?;
        
        match self.storage.top_up(&top_up.id, top_up.amount).await {
            Ok(new_amount) => Ok(Response::new(ItemTopUpResponse {
                amount: new_amount,
            })),
            Err(e) => Err(e.into()),
        }
    }
}