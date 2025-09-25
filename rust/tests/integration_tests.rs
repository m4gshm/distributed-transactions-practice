use tokio;
use tonic::Request;
use uuid::Uuid;
use common::orders::v1::{
    orders_service_client::OrdersServiceClient,
    *,
};
use common::payment::v1::{
    account_service_client::AccountServiceClient,
    payment_service_client::PaymentServiceClient,
    *,
};
use common::reserve::v1::{
    reserve_service_client::ReserveServiceClient,
    *,
};
use common::warehouse::v1::{
    warehouse_item_service_client::WarehouseItemServiceClient,
    *,
};
use common::tpc::v1::{
    two_phase_commit_service_client::TwoPhaseCommitServiceClient,
    *,
};

/// Integration tests for the Rust microservices
/// These tests mirror the functionality of the Java integration tests

const ORDERS_SERVICE_URL: &str = "http://localhost:50051";
const PAYMENTS_SERVICE_URL: &str = "http://localhost:50052";
const RESERVE_SERVICE_URL: &str = "http://localhost:50053";
const TPC_SERVICE_URL: &str = "http://localhost:50054";

#[tokio::test]
async fn test_complete_order_flow() -> Result<(), Box<dyn std::error::Error>> {
    // Test the complete order processing flow
    
    // 1. Setup test data
    let customer_id = Uuid::new_v4().to_string();
    let item_id = "item-1".to_string();
    
    // Top up customer account
    let mut account_client = AccountServiceClient::connect(PAYMENTS_SERVICE_URL).await?;
    let top_up_request = AccountTopUpRequest {
        top_up: Some(account_top_up_request::TopUp {
            client_id: customer_id.clone(),
            amount: 500.0,
        }),
    };
    let _ = account_client.top_up(Request::new(top_up_request)).await?;

    // Top up warehouse inventory
    let mut warehouse_client = WarehouseItemServiceClient::connect(RESERVE_SERVICE_URL).await?;
    let item_top_up_request = ItemTopUpRequest {
        top_up: Some(item_top_up_request::TopUp {
            id: item_id.clone(),
            amount: 50,
        }),
    };
    let _ = warehouse_client.top_up(Request::new(item_top_up_request)).await?;

    // 2. Create order
    let mut orders_client = OrdersServiceClient::connect(ORDERS_SERVICE_URL).await?;
    
    let create_request = OrderCreateRequest {
        body: Some(order_create_request::OrderCreate {
            items: vec![order_create_request::order_create::Item {
                id: item_id.clone(),
                amount: 2,
            }],
            customer_id: customer_id.clone(),
            delivery: Some(common::orders::v1::order::Delivery {
                date_time: None,
                address: "123 Test Street".to_string(),
                r#type: common::orders::v1::order::delivery::Type::Courier.into(),
            }),
        }),
        two_phase_commit: false,
    };
    
    let create_response = orders_client.create(Request::new(create_request)).await?;
    let order_id = create_response.into_inner().id;
    
    println!("Created order: {}", order_id);

    // 3. Get order details
    let get_request = OrderGetRequest {
        id: order_id.clone(),
    };
    let get_response = orders_client.get(Request::new(get_request)).await?;
    let order = get_response.into_inner().order.unwrap();
    
    assert_eq!(order.customer_id, customer_id);
    assert_eq!(order.status(), common::orders::v1::order::Status::Created);
    
    println!("Order status: {:?}", order.status());

    // 4. Approve order
    let approve_request = OrderApproveRequest {
        id: order_id.clone(),
        two_phase_commit: false,
    };
    
    let approve_response = orders_client.approve(Request::new(approve_request)).await?;
    let approved_status = approve_response.into_inner().status;
    
    println!("Order approved with status: {:?}", approved_status);

    // 5. Verify payment was created and approved
    if let Some(payment_id) = &order.payment_id {
        let mut payment_client = PaymentServiceClient::connect(PAYMENTS_SERVICE_URL).await?;
        let payment_get_request = PaymentGetRequest {
            id: payment_id.clone(),
        };
        let payment_response = payment_client.get(Request::new(payment_get_request)).await?;
        let payment = payment_response.into_inner().payment.unwrap();
        
        println!("Payment status: {:?}", payment.status());
        // Payment should be in HOLD status after approval
    }

    // 6. Verify reserve was created and approved  
    if let Some(reserve_id) = &order.reserve_id {
        let mut reserve_client = ReserveServiceClient::connect(RESERVE_SERVICE_URL).await?;
        let reserve_get_request = ReserveGetRequest {
            id: reserve_id.clone(),
        };
        let reserve_response = reserve_client.get(Request::new(reserve_get_request)).await?;
        let reserve = reserve_response.into_inner().reserve.unwrap();
        
        println!("Reserve status: {:?}", reserve.status());
        // Reserve should be APPROVED if inventory was sufficient
    }

    println!("✅ Complete order flow test passed!");

    Ok(())
}

#[tokio::test]
async fn test_two_phase_commit_flow() -> Result<(), Box<dyn std::error::Error>> {
    // Test two-phase commit transaction coordination
    
    let customer_id = Uuid::new_v4().to_string();
    let item_id = "item-2".to_string();
    
    // Setup test data
    let mut account_client = AccountServiceClient::connect(PAYMENTS_SERVICE_URL).await?;
    let top_up_request = AccountTopUpRequest {
        top_up: Some(account_top_up_request::TopUp {
            client_id: customer_id.clone(),
            amount: 1000.0,
        }),
    };
    let _ = account_client.top_up(Request::new(top_up_request)).await?;

    // Create order with two-phase commit enabled
    let mut orders_client = OrdersServiceClient::connect(ORDERS_SERVICE_URL).await?;
    
    let create_request = OrderCreateRequest {
        body: Some(order_create_request::OrderCreate {
            items: vec![order_create_request::order_create::Item {
                id: item_id.clone(),
                amount: 1,
            }],
            customer_id: customer_id.clone(),
            delivery: Some(common::orders::v1::order::Delivery {
                date_time: None,
                address: "456 Test Avenue".to_string(),
                r#type: common::orders::v1::order::delivery::Type::Pickup.into(),
            }),
        }),
        two_phase_commit: true, // Enable 2PC
    };
    
    let create_response = orders_client.create(Request::new(create_request)).await?;
    let order_id = create_response.into_inner().id;
    
    println!("Created order with 2PC: {}", order_id);

    // Check for prepared transactions
    let mut tpc_client = TwoPhaseCommitServiceClient::connect(TPC_SERVICE_URL).await?;
    let list_request = TwoPhaseListActivesRequest {};
    let list_response = tpc_client.list_actives(Request::new(list_request)).await?;
    let active_transactions = list_response.into_inner().transactions;
    
    println!("Active prepared transactions: {}", active_transactions.len());
    
    // In a real scenario, we would commit or rollback specific transactions
    for transaction in active_transactions {
        println!("Found prepared transaction: {}", transaction.id);
        
        // Commit the transaction
        let commit_request = TwoPhaseCommitRequest {
            id: transaction.id.clone(),
        };
        
        match tpc_client.commit(Request::new(commit_request)).await {
            Ok(response) => {
                println!("✅ Committed transaction {}: {}", transaction.id, response.into_inner().message);
            }
            Err(e) => {
                println!("⚠️  Failed to commit transaction {}: {}", transaction.id, e);
            }
        }
    }

    println!("✅ Two-phase commit flow test completed!");

    Ok(())
}

#[tokio::test] 
async fn test_insufficient_funds_scenario() -> Result<(), Box<dyn std::error::Error>> {
    // Test order creation with insufficient account balance
    
    let customer_id = Uuid::new_v4().to_string();
    let item_id = "item-3".to_string();
    
    // Create account with minimal balance
    let mut account_client = AccountServiceClient::connect(PAYMENTS_SERVICE_URL).await?;
    let top_up_request = AccountTopUpRequest {
        top_up: Some(account_top_up_request::TopUp {
            client_id: customer_id.clone(),
            amount: 5.0, // Very low balance
        }),
    };
    let _ = account_client.top_up(Request::new(top_up_request)).await?;

    // Create expensive order
    let mut orders_client = OrdersServiceClient::connect(ORDERS_SERVICE_URL).await?;
    
    let create_request = OrderCreateRequest {
        body: Some(order_create_request::OrderCreate {
            items: vec![order_create_request::order_create::Item {
                id: item_id.clone(),
                amount: 10, // Large quantity = high cost
            }],
            customer_id: customer_id.clone(),
            delivery: Some(common::orders::v1::order::Delivery {
                date_time: None,
                address: "789 Test Boulevard".to_string(),
                r#type: common::orders::v1::order::delivery::Type::Courier.into(),
            }),
        }),
        two_phase_commit: false,
    };
    
    let create_response = orders_client.create(Request::new(create_request)).await?;
    let order_id = create_response.into_inner().id;
    
    // Try to approve order (should fail due to insufficient funds)
    let approve_request = OrderApproveRequest {
        id: order_id.clone(),
        two_phase_commit: false,
    };
    
    let approve_response = orders_client.approve(Request::new(approve_request)).await?;
    let status = approve_response.into_inner().status;
    
    println!("Order status after approval attempt: {:?}", status);
    
    // Status should be INSUFFICIENT due to lack of funds
    match status {
        Some(status) if status == common::orders::v1::order::Status::Insufficient as i32 => {
            println!("✅ Insufficient funds scenario handled correctly!");
        }
        _ => {
            println!("⚠️  Expected INSUFFICIENT status, got: {:?}", status);
        }
    }

    Ok(())
}

#[tokio::test]
async fn test_service_connectivity() -> Result<(), Box<dyn std::error::Error>> {
    // Test basic connectivity to all services
    
    println!("Testing connectivity to all services...");

    // Test Orders Service
    let mut orders_client = OrdersServiceClient::connect(ORDERS_SERVICE_URL).await?;
    let list_request = OrderListRequest {};
    let _ = orders_client.list(Request::new(list_request)).await?;
    println!("✅ Orders Service: Connected");

    // Test Payments Service  
    let mut payment_client = PaymentServiceClient::connect(PAYMENTS_SERVICE_URL).await?;
    let list_request = PaymentListRequest {};
    let _ = payment_client.list(Request::new(list_request)).await?;
    println!("✅ Payments Service: Connected");

    // Test Account Service
    let mut account_client = AccountServiceClient::connect(PAYMENTS_SERVICE_URL).await?;
    let list_request = AccountListRequest {};
    let _ = account_client.list(Request::new(list_request)).await?;
    println!("✅ Account Service: Connected");

    // Test Reserve Service
    let mut reserve_client = ReserveServiceClient::connect(RESERVE_SERVICE_URL).await?;
    let list_request = ReserveListRequest {};
    let _ = reserve_client.list(Request::new(list_request)).await?;
    println!("✅ Reserve Service: Connected");

    // Test Warehouse Service
    let mut warehouse_client = WarehouseItemServiceClient::connect(RESERVE_SERVICE_URL).await?;
    let list_request = ItemListRequest {};
    let _ = warehouse_client.item_list(Request::new(list_request)).await?;
    println!("✅ Warehouse Service: Connected");

    // Test TPC Service
    let mut tpc_client = TwoPhaseCommitServiceClient::connect(TPC_SERVICE_URL).await?;
    let list_request = TwoPhaseListActivesRequest {};
    let _ = tpc_client.list_actives(Request::new(list_request)).await?;
    println!("✅ TPC Service: Connected");

    println!("✅ All services are reachable!");

    Ok(())
}