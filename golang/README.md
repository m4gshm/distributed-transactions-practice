# Distributed Transactions Practice - Go Implementation

This is a Go implementation of the distributed transactions practice project, featuring microservices architecture with gRPC communication and two-phase commit transactions.

## Architecture

The system consists of 4 microservices:

1. **Orders Service** (Port 9001) - Manages order creation and lifecycle
2. **Payments Service** (Port 9002) - Handles payments and account management  
3. **Reserve Service** (Port 9003) - Manages inventory reservations and warehouse items
4. **Two-Phase Commit Service** (Port 9004) - Coordinates distributed transactions

## Features

- gRPC-based microservices communication
- Two-phase commit for distributed transactions
- PostgreSQL with prepared transactions support
- Docker containerization
- Database migrations
- RESTful HTTP endpoints (via gRPC-Gateway annotations)

## Services

### Orders Service
- Create orders with multiple items
- Approve/cancel/release orders
- Order lifecycle management
- Integration with payments and inventory

### Payments Service  
- Payment creation and processing
- Account balance management
- Payment hold/release functionality
- Insufficient funds handling

### Reserve Service
- Inventory item reservation
- Warehouse stock management
- Item availability checking
- Stock top-up functionality

### TPC Service
- Two-phase commit coordination
- Prepared transaction management
- Transaction rollback support

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Build and start all services
docker-compose up --build

# Or start specific services
docker-compose up postgres
docker-compose up orders-service payments-service
```

### Manual Build & Run

#### Prerequisites
- Go 1.24+
- PostgreSQL 15+

#### Build
```bash
# Windows
.\build-and-run.bat

# Linux/macOS  
chmod +x build-and-run.sh
./build-and-run.sh
```

#### Run Services
```bash
# Start each service in separate terminals
./bin/orders
./bin/payments  
./bin/reserve
./bin/tpc
```

## Configuration

Services are configured via environment variables:

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=postgres
DB_NAME=distributed_transactions
DB_SSL_MODE=disable

# Service ports
ORDERS_PORT=9001
PAYMENTS_PORT=9002
RESERVE_PORT=9003
TPC_PORT=9004
```

## Database Schema

The system automatically runs migrations on startup, creating:

- `orders` - Order management
- `order_items` - Order line items
- `payments` - Payment transactions
- `accounts` - Customer account balances
- `reserves` - Inventory reservations
- `reserve_items` - Reserved item details
- `warehouse_items` - Available inventory

Sample data is included for testing.

## API Examples

### Create Order
```bash
grpcurl -plaintext -d '{
  "body": {
    "customer_id": "customer-1",
    "items": [{"id": "item-1", "amount": 2}],
    "delivery": {
      "address": "123 Main St",
      "type": "COURIER"
    }
  }
}' localhost:9001 orders.v1.OrdersService/Create
```

### Top Up Account
```bash
grpcurl -plaintext -d '{
  "top_up": {
    "client_id": "customer-1", 
    "amount": 500.0
  }
}' localhost:9002 payment.v1.AccountService/TopUp
```

### Add Warehouse Stock
```bash
grpcurl -plaintext -d '{
  "top_up": {
    "id": "item-1",
    "amount": 100  
  }
}' localhost:9003 warehouse.v1.WarehouseItemService/TopUp
```

## Testing

The implementation follows the same patterns as the Java reference implementation:

- Service boundary consistency
- Database schema compatibility  
- gRPC API contracts
- Two-phase commit semantics
- Error handling patterns

## Project Structure

```
golang/
├── cmd/                    # Service main files
│   ├── orders/
│   ├── payments/
│   ├── reserve/
│   └── tpc/
├── internal/               # Internal packages
│   ├── config/            # Configuration management
│   ├── database/          # Database connection & migrations
│   ├── models/            # Data models
│   ├── orders/            # Orders service implementation
│   ├── payments/          # Payments service implementation
│   ├── reserve/           # Reserve service implementation
│   └── tpc/               # TPC service implementation
├── gen/                   # Generated protobuf code
├── migrations/            # Database migration scripts
├── docker-compose.yml     # Docker orchestration
├── Dockerfile.*           # Service-specific Dockerfiles
└── build-and-run.*        # Build scripts
```

## Development

This implementation maintains the same service boundaries, API contracts, and transaction semantics as the original Java implementation while leveraging Go's advantages like built-in concurrency and simpler deployment.

Key Go-specific patterns used:
- Contexts for cancellation and timeouts
- Structured error handling with status codes
- Database/sql for PostgreSQL integration
- Standard library HTTP server capabilities
- Minimal external dependencies