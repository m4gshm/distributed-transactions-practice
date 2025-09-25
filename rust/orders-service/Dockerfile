# Multi-stage Dockerfile for Orders Service
FROM rust:1.75 as builder

WORKDIR /app

# Copy workspace files
COPY Cargo.toml ./
COPY common ./common
COPY proto-build ./proto-build
COPY orders-service ./orders-service

# Copy proto files
COPY ../proto ./proto

# Build the application
RUN cargo build --release --bin orders-service

# Runtime stage
FROM debian:bookworm-slim

# Install SSL certificates and other runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/target/release/orders-service /app/orders-service

# Expose the port
EXPOSE 50051

# Run the service
CMD ["./orders-service"]