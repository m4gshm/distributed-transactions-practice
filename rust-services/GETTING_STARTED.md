# Getting Started with Rust Services

This guide will help you set up and run the Rust implementation of the distributed transactions microservices.

## Prerequisites

- Rust 1.75 or later
- Docker and Docker Compose
- PostgreSQL client (optional, for manual database operations)

## Quick Start

### 1. Clone and Navigate
```bash
cd rust-services
```

### 2. Start Infrastructure
```bash
# Start PostgreSQL, Kafka, and Zookeeper
docker-compose up -d postgres kafka zookeeper

# Wait for services to start
sleep 10
```

### 3. Initialize Database
```bash
# Apply database schema
PGPASSWORD=postgres psql -h localhost -U postgres -d postgres -f schema.sql
```

### 4. Build Services
```bash
# Build all services
cargo build --release
```

### 5. Run Services

#### Option A: Run services locally
```bash
# Terminal 1 - Orders Service (port 50051)
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres cargo run --bin orders-service

# Terminal 2 - Payments Service (port 50052)  
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres KAFKA_BROKER=localhost:9092 cargo run --bin payments-service

# Terminal 3 - Reserve Service (port 50053)
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres cargo run --bin reserve-service

# Terminal 4 - TPC Service (port 50054)
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres cargo run --bin tpc-service
```

#### Option B: Run with Docker
```bash
# Build and run all services
docker-compose up --build
```

## Testing

### Run Integration Tests
```bash
# Make sure services are running, then:
cargo test --test integration_tests
```

### Manual Testing with grpcurl

#### Create Order
```bash
grpcurl -plaintext -d '{
  "body": {
    "items": [{"id": "item-1", "amount": 2}],
    "customer_id": "11111111-1111-1111-1111-111111111111",
    "delivery": {
      "address": "123 Test St",
      "type": "COURIER"
    }
  },
  "two_phase_commit": false
}' localhost:50051 orders.v1.OrdersService/Create
```

#### Get Order
```bash
grpcurl -plaintext -d '{"id": "ORDER_ID_HERE"}' localhost:50051 orders.v1.OrdersService/Get
```

#### Approve Order
```bash
grpcurl -plaintext -d '{"id": "ORDER_ID_HERE", "two_phase_commit": false}' localhost:50051 orders.v1.OrdersService/Approve
```

#### List Accounts
```bash
grpcurl -plaintext -d '{}' localhost:50052 payment.v1.AccountService/List
```

#### Top Up Account
```bash
grpcurl -plaintext -d '{
  "top_up": {
    "client_id": "11111111-1111-1111-1111-111111111111", 
    "amount": 500.0
  }
}' localhost:50052 payment.v1.AccountService/TopUp
```

#### List Warehouse Items
```bash
grpcurl -plaintext -d '{}' localhost:50053 warehouse.v1.WarehouseItemService/ItemList
```

#### List Active Prepared Transactions
```bash
grpcurl -plaintext -d '{}' localhost:50054 tpc.v1.TwoPhaseCommitService/listActives
```

## Service Architecture

### Port Assignments
- **Orders Service**: 50051
- **Payments Service**: 50052 (includes Account Service)
- **Reserve Service**: 50053 (includes Warehouse Service)
- **TPC Service**: 50054

### Service Dependencies
```mermaid
graph TB
    Orders[Orders Service<br/>50051] --> Payments[Payments Service<br/>50052]
    Orders --> Reserve[Reserve Service<br/>50053]
    Orders --> TPC_Orders[TPC Service<br/>50054]
    
    Payments --> TPC_Payments[TPC Service<br/>50054]
    Reserve --> TPC_Reserve[TPC Service<br/>50054]
    
    Payments --> Kafka[Kafka<br/>9092]
    
    Orders --> DB[(PostgreSQL<br/>5432)]
    Payments --> DB
    Reserve --> DB
    TPC_Orders --> DB
```

## Key Features Implemented

### ✅ Distributed Transactions
- Two-phase commit protocol
- Prepared transaction management
- Transaction coordination across services

### ✅ Reactive Programming
- Async/await throughout
- Non-blocking I/O with sqlx
- Stream processing with tonic

### ✅ Event-Driven Architecture  
- Kafka integration for account balance events
- Idempotent message processing
- Event sourcing patterns

### ✅ Microservices Patterns
- Service discovery via configuration
- Circuit breaker patterns (via tonic retry)
- Distributed tracing ready (OpenTelemetry compatible)

### ✅ Data Consistency
- ACID transactions within services
- Eventual consistency across services
- Status-based state machines

## Configuration

### Environment Variables
- `DATABASE_URL`: PostgreSQL connection string
- `KAFKA_BROKER`: Kafka broker address
- `SERVER_PORT`: Service port (default varies by service)
- `SERVER_HOST`: Bind address (default: 0.0.0.0)
- `RUST_LOG`: Logging level (info, debug, trace)

### Database Schema
The schema is automatically applied via Docker initialization scripts. For manual setup, run:
```bash
psql -h localhost -U postgres -d postgres -f schema.sql
```

## Troubleshooting

### Database Connection Issues
```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Check logs
docker-compose logs postgres

# Connect manually
PGPASSWORD=postgres psql -h localhost -U postgres -d postgres
```

### Service Connection Issues
```bash
# Check service logs
docker-compose logs orders-service-rust

# Test gRPC connectivity
grpcurl -plaintext localhost:50051 list
```

### Build Issues
```bash
# Clean build
cargo clean
cargo build

# Update dependencies
cargo update
```

## Development

### Project Structure
```
rust-services/
├── common/              # Shared utilities and proto definitions
├── proto-build/         # Protocol buffer compilation
├── orders-service/      # Order management service
├── payments-service/    # Payment and account management
├── reserve-service/     # Inventory reservation
├── tpc-service/         # Two-phase commit coordinator
├── tests/              # Integration tests
└── schema.sql          # Database schema
```

### Adding New Features
1. Update protobuf definitions in `../proto/`
2. Regenerate Rust code: `cargo build` (automatic via build.rs)
3. Implement service logic in respective service modules
4. Add integration tests in `tests/`
5. Update documentation

### Performance Monitoring
Services include structured logging with tracing. To enable detailed logging:
```bash
RUST_LOG=debug cargo run --bin orders-service
```

## Production Considerations

### Security
- TLS termination via reverse proxy
- JWT authentication (not implemented)
- Network policies for service isolation

### Monitoring
- OpenTelemetry tracing integration
- Prometheus metrics (can be added)
- Health check endpoints (can be added)

### Scalability
- Horizontal scaling via load balancer
- Database connection pooling (implemented)
- Async I/O for high concurrency

### Reliability
- Graceful shutdown handling
- Circuit breakers for external calls
- Retry policies with exponential backoff