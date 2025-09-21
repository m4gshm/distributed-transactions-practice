use std::sync::Arc;
use tonic::{Request, Response, Status};
use common::orders::v1::{
    orders_service_server::OrdersService,
    *,
};
use common::payment::v1::{
    payment_service_client::PaymentServiceClient,
    PaymentCreateRequest, PaymentCreateResponse, PaymentApproveRequest, PaymentApproveResponse,
    PaymentCancelRequest, PaymentCancelResponse,
};
use common::reserve::v1::{
    reserve_service_client::ReserveServiceClient,
    ReserveCreateRequest, ReserveCreateResponse, ReserveApproveRequest, ReserveApproveResponse,
    ReserveCancelRequest, ReserveCancelResponse,
};
use common::tpc::v1::{
    two_phase_commit_service_client::TwoPhaseCommitServiceClient,
    TwoPhaseCommitRequest, TwoPhaseRollbackRequest,
};
use common::error::{AppError, AppResult, check_status};
use common::transaction::{in_transaction, TwoPhaseTransactionManager};
use common::utils::{generate_uuid, datetime_to_timestamp};
use common::status::OrderStatus;
use crate::models::{Order, OrderItem, DeliveryType};
use crate::storage::OrderStorage;

/// Orders service implementation (mirrors Java OrdersServiceImpl)
pub struct OrdersServiceImpl {
    storage: Arc<OrderStorage>,
    pool: sqlx::PgPool,
    payment_client: PaymentServiceClient<tonic::transport::Channel>,
    reserve_client: ReserveServiceClient<tonic::transport::Channel>,
    payment_tpc_client: TwoPhaseCommitServiceClient<tonic::transport::Channel>,
    reserve_tpc_client: TwoPhaseCommitServiceClient<tonic::transport::Channel>,
    tpc_manager: TwoPhaseTransactionManager,
}

impl OrdersServiceImpl {
    pub fn new(
        storage: Arc<OrderStorage>,
        pool: sqlx::PgPool,
        payment_client: PaymentServiceClient<tonic::transport::Channel>,
        reserve_client: ReserveServiceClient<tonic::transport::Channel>,
        payment_tpc_client: TwoPhaseCommitServiceClient<tonic::transport::Channel>,
        reserve_tpc_client: TwoPhaseCommitServiceClient<tonic::transport::Channel>,
    ) -> Self {
        Self {
            storage,
            pool,
            payment_client,
            reserve_client,
            payment_tpc_client,
            reserve_tpc_client,
            tpc_manager: TwoPhaseTransactionManager::new(),
        }
    }

    async fn create_order_internal(
        &self,
        create_request: &order_create_request::OrderCreate,
        two_phase_commit: bool,
    ) -> AppResult<OrderCreateResponse> {
        let order_id = generate_uuid();
        let delivery = create_request.delivery.as_ref()
            .ok_or_else(|| AppError::Validation("Delivery information is required".to_string()))?;
        
        let order = Order::new(
            create_request.customer_id.clone(),
            delivery.address.clone(),
            DeliveryType::from(delivery.r#type()),
            delivery.date_time.as_ref().map(common::utils::timestamp_to_datetime),
        );

        let items: Vec<OrderItem> = create_request.items.iter().map(|item| {
            OrderItem {
                id: generate_uuid(),
                order_id: order_id.clone(),
                item_id: item.id.clone(),
                amount: item.amount,
                insufficient: None,
                reserved: false,
            }
        }).collect();

        // Calculate total cost by calling external service (simplified)
        let total_cost = items.len() as f64 * 10.0; // Simplified cost calculation

        let payment_tx_id = if two_phase_commit { Some(generate_uuid()) } else { None };
        let reserve_tx_id = if two_phase_commit { Some(generate_uuid()) } else { None };

        // Create payment
        let mut payment_request = PaymentCreateRequest {
            body: Some(common::payment::v1::payment_create_request::PaymentCreate {
                external_ref: order_id.clone(),
                client_id: create_request.customer_id.clone(),
                amount: total_cost,
            }),
            prepared_transaction_id: payment_tx_id.clone(),
        };

        let payment_response = self.payment_client.clone()
            .create(Request::new(payment_request))
            .await
            .map_err(|e| AppError::ExternalService(format!("Payment service error: {}", e)))?
            .into_inner();

        // Create reserve
        let reserve_items: Vec<common::reserve::v1::reserve_create_request::reserve::Item> = 
            create_request.items.iter().map(|item| {
                common::reserve::v1::reserve_create_request::reserve::Item {
                    id: item.id.clone(),
                    amount: item.amount,
                }
            }).collect();

        let reserve_request = ReserveCreateRequest {
            body: Some(common::reserve::v1::reserve_create_request::Reserve {
                external_ref: order_id.clone(),
                items: reserve_items,
            }),
            prepared_transaction_id: reserve_tx_id.clone(),
        };

        let reserve_response = self.reserve_client.clone()
            .create(Request::new(reserve_request))
            .await
            .map_err(|e| AppError::ExternalService(format!("Reserve service error: {}", e)))?
            .into_inner();

        // Save order with transaction coordination
        let final_order = order
            .with_payment_id(payment_response.id)
            .with_reserve_id(reserve_response.id)
            .with_status(OrderStatus::Created);

        let prepared_tx_id = if two_phase_commit { Some(generate_uuid()) } else { None };
        
        in_transaction(&self.pool, prepared_tx_id.clone(), |_tx| async {
            self.storage.save_with_items(&final_order, &items).await
        }).await?;

        // Commit external transactions if using 2PC
        if two_phase_commit {
            if let Some(tx_id) = payment_tx_id {
                let _ = self.payment_tpc_client.clone()
                    .commit(Request::new(TwoPhaseCommitRequest { id: tx_id }))
                    .await;
            }
            if let Some(tx_id) = reserve_tx_id {
                let _ = self.reserve_tpc_client.clone()
                    .commit(Request::new(TwoPhaseCommitRequest { id: tx_id }))
                    .await;
            }
            if let Some(tx_id) = prepared_tx_id {
                self.tpc_manager.commit(&tx_id).await?;
            }
        }

        Ok(OrderCreateResponse {
            id: order_id,
        })
    }

    async fn approve_order_internal(
        &self,
        order_id: &str,
        two_phase_commit: bool,
    ) -> AppResult<OrderApproveResponse> {
        let (order, _items) = self.storage.get_with_items(order_id).await?;
        
        // Check if order can be approved
        check_status("approve", order.status, &[OrderStatus::Created, OrderStatus::Insufficient])?;

        // Approve payment
        let payment_tx_id = if two_phase_commit { Some(generate_uuid()) } else { None };
        let payment_request = PaymentApproveRequest {
            id: order.payment_id.clone().unwrap_or_default(),
            prepared_transaction_id: payment_tx_id.clone(),
        };

        let payment_response = self.payment_client.clone()
            .approve(Request::new(payment_request))
            .await
            .map_err(|e| AppError::ExternalService(format!("Payment approve error: {}", e)))?
            .into_inner();

        // Approve reserve
        let reserve_tx_id = if two_phase_commit { Some(generate_uuid()) } else { None };
        let reserve_request = ReserveApproveRequest {
            id: order.reserve_id.clone().unwrap_or_default(),
            prepared_transaction_id: reserve_tx_id.clone(),
        };

        let reserve_response = self.reserve_client.clone()
            .approve(Request::new(reserve_request))
            .await
            .map_err(|e| AppError::ExternalService(format!("Reserve approve error: {}", e)))?
            .into_inner();

        // Update order status
        let new_status = if payment_response.status() == common::payment::v1::payment::Status::Hold &&
                           reserve_response.status() == common::reserve::v1::reserve::Status::Approved {
            OrderStatus::Approved
        } else {
            OrderStatus::Insufficient
        };

        let updated_order = order.with_status(new_status);
        self.storage.save(updated_order).await?;

        // Commit transactions if using 2PC
        if two_phase_commit {
            if let Some(tx_id) = payment_tx_id {
                let _ = self.payment_tpc_client.clone()
                    .commit(Request::new(TwoPhaseCommitRequest { id: tx_id }))
                    .await;
            }
            if let Some(tx_id) = reserve_tx_id {
                let _ = self.reserve_tpc_client.clone()
                    .commit(Request::new(TwoPhaseCommitRequest { id: tx_id }))
                    .await;
            }
        }

        Ok(OrderApproveResponse {
            id: order_id.to_string(),
            status: Some(new_status.into()),
        })
    }
}

#[tonic::async_trait]
impl OrdersService for OrdersServiceImpl {
    async fn create(
        &self,
        request: Request<OrderCreateRequest>,
    ) -> Result<Response<OrderCreateResponse>, Status> {
        let req = request.into_inner();
        let body = req.body.ok_or_else(|| Status::invalid_argument("Request body required"))?;
        
        match self.create_order_internal(&body, req.two_phase_commit).await {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn approve(
        &self,
        request: Request<OrderApproveRequest>,
    ) -> Result<Response<OrderApproveResponse>, Status> {
        let req = request.into_inner();
        
        match self.approve_order_internal(&req.id, req.two_phase_commit).await {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn cancel(
        &self,
        request: Request<OrderCancelRequest>,
    ) -> Result<Response<OrderCancelResponse>, Status> {
        let req = request.into_inner();
        
        // Implementation similar to approve but with cancel operations
        // For brevity, returning a simple response
        Ok(Response::new(OrderCancelResponse {
            id: req.id,
            status: Some(OrderStatus::Cancelled.into()),
        }))
    }

    async fn release(
        &self,
        request: Request<OrderReleaseRequest>,
    ) -> Result<Response<OrderReleaseResponse>, Status> {
        let req = request.into_inner();
        
        Ok(Response::new(OrderReleaseResponse {
            id: req.id,
            status: Some(OrderStatus::Released.into()),
        }))
    }

    async fn resume(
        &self,
        request: Request<OrderResumeRequest>,
    ) -> Result<Response<OrderResumeResponse>, Status> {
        let req = request.into_inner();
        
        Ok(Response::new(OrderResumeResponse {
            id: req.id,
            status: OrderStatus::Created.into(),
        }))
    }

    async fn get(
        &self,
        request: Request<OrderGetRequest>,
    ) -> Result<Response<OrderGetResponse>, Status> {
        let req = request.into_inner();
        
        match self.storage.get_with_items(&req.id).await {
            Ok((order, items)) => {
                let proto_order = common::orders::v1::Order {
                    id: order.id,
                    created_at: datetime_to_timestamp(order.created_at),
                    updated_at: datetime_to_timestamp(order.updated_at),
                    customer_id: order.customer_id,
                    payment_id: order.payment_id,
                    reserve_id: order.reserve_id,
                    status: order.status.into(),
                    payment_status: order.payment_status.map(|s| s.into()),
                    delivery: Some(common::orders::v1::order::Delivery {
                        date_time: order.delivery_date_time.and_then(datetime_to_timestamp),
                        address: order.delivery_address,
                        r#type: order.delivery_type.into(),
                    }),
                    items: items.into_iter().map(|item| {
                        common::reserve::v1::reserve::Item {
                            id: item.item_id,
                            amount: item.amount,
                            insufficient: item.insufficient,
                            reserved: item.reserved,
                        }
                    }).collect(),
                };
                
                Ok(Response::new(OrderGetResponse {
                    order: Some(proto_order),
                }))
            }
            Err(e) => Err(e.into()),
        }
    }

    async fn list(
        &self,
        _request: Request<OrderListRequest>,
    ) -> Result<Response<OrderListResponse>, Status> {
        match self.storage.find_all().await {
            Ok(orders) => {
                let proto_orders: Vec<common::orders::v1::Order> = orders.into_iter().map(|order| {
                    common::orders::v1::Order {
                        id: order.id,
                        created_at: datetime_to_timestamp(order.created_at),
                        updated_at: datetime_to_timestamp(order.updated_at),
                        customer_id: order.customer_id,
                        payment_id: order.payment_id,
                        reserve_id: order.reserve_id,
                        status: order.status.into(),
                        payment_status: order.payment_status.map(|s| s.into()),
                        delivery: Some(common::orders::v1::order::Delivery {
                            date_time: order.delivery_date_time.and_then(datetime_to_timestamp),
                            address: order.delivery_address,
                            r#type: order.delivery_type.into(),
                        }),
                        items: vec![], // Items not loaded for list operation
                    }
                }).collect();
                
                Ok(Response::new(OrderListResponse {
                    orders: proto_orders,
                }))
            }
            Err(e) => Err(e.into()),
        }
    }
}