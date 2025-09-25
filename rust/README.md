# Rust Implementation of Distributed Transactions Practice

This directory contains Rust implementations of the gRPC services defined in the parent Java project. The Rust services mirror the functionality and architecture patterns of the Java implementation while leveraging Rust's type safety and performance characteristics.

## Architecture

The Rust implementation follows the same microservices architecture as the Java version:

- **Orders Service**: Orchestrates order lifecycle with distributed transactions
- **Payments Service**: Manages payment processing and account operations  
- **Reserve Service**: Handles inventory reservations
- **TPC Service**: Coordinates two-phase commit transactions
- **Common**: Shared utilities, error types, and database abstractions

## Key Technologies

- **tonic**: gRPC framework for Rust
- **sqlx**: Async PostgreSQL driver with compile-time checked queries
- **tokio**: Async runtime
- **tracing**: Structured logging and observability
- **opentelemetry**: Distributed tracing (matches Java OpenTelemetry setup)

## Design Patterns

The implementation follows these patterns from the Java version:

1. **Reactive Programming**: Using Rust's async/await and streams
2. **Two-Phase Commit**: Distributed transaction coordination
3. **Event-Driven Architecture**: Kafka integration for events
4. **Idempotent Operations**: Consistent handling of duplicate requests
5. **Status-Based State Machines**: Order/Payment/Reserve status transitions

## Service Structure

Each service follows a consistent structure:
```
service-name/
├── src/
│   ├── main.rs          # Service entry point
│   ├── service.rs       # gRPC service implementation
│   ├── storage.rs       # Database operations
│   ├── models.rs        # Domain models
│   └── config.rs        # Configuration
├── Cargo.toml
└── README.md
```

## Getting Started

1. Ensure PostgreSQL is running (use docker-compose from parent directory)
2. Build all services: `cargo build`
3. Run individual services: `cargo run -p <service-name>`

## Observability

The services include OpenTelemetry tracing that integrates with the existing Grafana/Prometheus/Tempo stack from the Java implementation.