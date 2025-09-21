# Multi-stage Dockerfile for TPC Service
FROM rust:1.75 as builder

WORKDIR /app

# Copy workspace files
COPY Cargo.toml ./
COPY common ./common
COPY proto-build ./proto-build
COPY tpc-service ./tpc-service

# Copy proto files
COPY ../proto ./proto

# Build the application
RUN cargo build --release --bin tpc-service

# Runtime stage
FROM debian:bookworm-slim

# Install SSL certificates and other runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/target/release/tpc-service /app/tpc-service

# Expose the port
EXPOSE 50054

# Run the service
CMD ["./tpc-service"]