use std::sync::Arc;
use tonic::{Request, Response, Status};
use common::payment::v1::{
    payment_service_server::PaymentService,
    account_service_server::AccountService,
    *,
};
use common::error::{AppError, AppResult, check_status};
use common::transaction::in_transaction;
use common::utils::{generate_uuid, datetime_to_timestamp};
use common::status::PaymentStatus;
use crate::models::{Payment, Account};
use crate::storage::{PaymentStorage, AccountStorage};
use crate::events::AccountEventService;

/// Payment service implementation (mirrors Java PaymentServiceImpl)
pub struct PaymentServiceImpl {
    storage: Arc<PaymentStorage>,
    account_storage: Arc<AccountStorage>,
    pool: sqlx::PgPool,
}

impl PaymentServiceImpl {
    pub fn new(
        storage: Arc<PaymentStorage>,
        account_storage: Arc<AccountStorage>,
        pool: sqlx::PgPool,
    ) -> Self {
        Self {
            storage,
            account_storage,
            pool,
        }
    }

    async fn payment_with_account_operation<F, Fut, T>(
        &self,
        operation_name: &str,
        prepared_transaction_id: Option<String>,
        payment_id: &str,
        expected_statuses: &[PaymentStatus],
        operation: F,
    ) -> AppResult<T>
    where
        F: FnOnce(Payment, Account) -> Fut,
        Fut: std::future::Future<Output = AppResult<T>>,
    {
        in_transaction(&self.pool, prepared_transaction_id, |_tx| async {
            let payment = self.storage.get_by_id(payment_id.to_string()).await?;
            check_status(operation_name, payment.status, expected_statuses)?;
            
            let account = self.account_storage.get_by_id(payment.client_id.clone()).await?;
            operation(payment, account).await
        }).await
    }
}

#[tonic::async_trait]
impl PaymentService for PaymentServiceImpl {
    async fn create(
        &self,
        request: Request<PaymentCreateRequest>,
    ) -> Result<Response<PaymentCreateResponse>, Status> {
        let req = request.into_inner();
        let body = req.body.ok_or_else(|| Status::invalid_argument("Request body required"))?;
        
        let payment_id = generate_uuid();
        let payment = Payment::new(body.external_ref, body.client_id, body.amount);
        
        let result = in_transaction(&self.pool, req.prepared_transaction_id, |_tx| async {
            self.storage.save(payment).await?;
            Ok(PaymentCreateResponse {
                id: payment_id.clone(),
            })
        }).await;

        match result {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn approve(
        &self,
        request: Request<PaymentApproveRequest>,
    ) -> Result<Response<PaymentApproveResponse>, Status> {
        let req = request.into_inner();
        
        let result = self.payment_with_account_operation(
            "approve",
            req.prepared_transaction_id,
            &req.id,
            &[PaymentStatus::Created, PaymentStatus::Insufficient],
            |payment, account| async move {
                let lock_result = self.account_storage.add_lock(&account.client_id, payment.amount).await?;
                
                let new_status = if lock_result.success {
                    PaymentStatus::Hold
                } else {
                    PaymentStatus::Insufficient
                };
                
                let updated_payment = payment
                    .with_status(new_status)
                    .with_insufficient(if lock_result.success { None } else { Some(lock_result.insufficient_amount) });
                
                self.storage.save(updated_payment).await?;
                
                Ok(PaymentApproveResponse {
                    id: req.id.clone(),
                    status: new_status.into(),
                    insufficient_amount: lock_result.insufficient_amount,
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
        request: Request<PaymentCancelRequest>,
    ) -> Result<Response<PaymentCancelResponse>, Status> {
        let req = request.into_inner();
        
        let result = self.payment_with_account_operation(
            "cancel",
            req.prepared_transaction_id,
            &req.id,
            &[PaymentStatus::Created, PaymentStatus::Insufficient, PaymentStatus::Hold],
            |payment, account| async move {
                // Unlock funds if they were locked
                self.account_storage.unlock(&account.client_id, payment.amount).await?;
                
                let updated_payment = payment.with_status(PaymentStatus::Cancelled);
                self.storage.save(updated_payment).await?;
                
                Ok(PaymentCancelResponse {
                    id: req.id.clone(),
                    status: PaymentStatus::Cancelled.into(),
                })
            }
        ).await;

        match result {
            Ok(response) => Ok(Response::new(response)),
            Err(e) => Err(e.into()),
        }
    }

    async fn pay(
        &self,
        request: Request<PaymentPayRequest>,
    ) -> Result<Response<PaymentPayResponse>, Status> {
        let req = request.into_inner();
        
        let result = self.payment_with_account_operation(
            "pay",
            req.prepared_transaction_id,
            &req.id,
            &[PaymentStatus::Hold],
            |payment, account| async move {
                let write_off_result = self.account_storage.write_off(&account.client_id, payment.amount).await?;
                
                let updated_payment = payment.with_status(PaymentStatus::Paid);
                self.storage.save(updated_payment).await?;
                
                Ok(PaymentPayResponse {
                    id: req.id.clone(),
                    status: PaymentStatus::Paid.into(),
                    balance: write_off_result.balance,
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
        request: Request<PaymentGetRequest>,
    ) -> Result<Response<PaymentGetResponse>, Status> {
        let req = request.into_inner();
        
        match self.storage.get_by_id(req.id).await {
            Ok(payment) => {
                let proto_payment = common::payment::v1::Payment {
                    external_ref: payment.external_ref,
                    client_id: payment.client_id,
                    amount: payment.amount,
                    insufficient: payment.insufficient,
                    status: payment.status.into(),
                };
                
                Ok(Response::new(PaymentGetResponse {
                    payment: Some(proto_payment),
                }))
            }
            Err(e) => Err(e.into()),
        }
    }

    async fn list(
        &self,
        _request: Request<PaymentListRequest>,
    ) -> Result<Response<PaymentListResponse>, Status> {
        match self.storage.find_all().await {
            Ok(payments) => {
                let proto_payments: Vec<common::payment::v1::Payment> = payments.into_iter().map(|payment| {
                    common::payment::v1::Payment {
                        external_ref: payment.external_ref,
                        client_id: payment.client_id,
                        amount: payment.amount,
                        insufficient: payment.insufficient,
                        status: payment.status.into(),
                    }
                }).collect();
                
                Ok(Response::new(PaymentListResponse {
                    payments: proto_payments,
                }))
            }
            Err(e) => Err(e.into()),
        }
    }
}

/// Account service implementation (mirrors Java AccountServiceImpl)
pub struct AccountServiceImpl {
    storage: Arc<AccountStorage>,
    event_service: AccountEventService,
}

impl AccountServiceImpl {
    pub fn new(storage: Arc<AccountStorage>, event_service: AccountEventService) -> Self {
        Self {
            storage,
            event_service,
        }
    }
}

#[tonic::async_trait]
impl AccountService for AccountServiceImpl {
    async fn list(
        &self,
        _request: Request<AccountListRequest>,
    ) -> Result<Response<AccountListResponse>, Status> {
        match self.storage.find_all().await {
            Ok(accounts) => {
                let proto_accounts: Vec<common::payment::v1::Account> = accounts.into_iter().map(|account| {
                    common::payment::v1::Account {
                        client_id: account.client_id,
                        amount: account.amount,
                        locked: account.locked,
                        updated_at: datetime_to_timestamp(account.updated_at),
                    }
                }).collect();
                
                Ok(Response::new(AccountListResponse {
                    accounts: proto_accounts,
                }))
            }
            Err(e) => Err(e.into()),
        }
    }

    async fn top_up(
        &self,
        request: Request<AccountTopUpRequest>,
    ) -> Result<Response<AccountTopUpResponse>, Status> {
        let req = request.into_inner();
        let top_up = req.top_up.ok_or_else(|| Status::invalid_argument("TopUp data required"))?;
        
        match self.storage.add_amount(&top_up.client_id, top_up.amount).await {
            Ok(result) => {
                // Send account balance event (matching Java implementation)
                if let Err(e) = self.event_service.send_account_balance_event(
                    &top_up.client_id,
                    result.balance,
                    result.timestamp,
                ).await {
                    tracing::error!("Failed to send account balance event: {}", e);
                }
                
                Ok(Response::new(AccountTopUpResponse {
                    balance: result.balance,
                }))
            }
            Err(e) => Err(e.into()),
        }
    }
}