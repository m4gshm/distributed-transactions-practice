# Multi-stage Dockerfile for Reserve Service
FROM rust:1.75 as builder

WORKDIR /app

# Copy workspace files
COPY Cargo.toml ./
COPY common ./common
COPY proto-build ./proto-build
COPY reserve-service ./reserve-service

# Copy proto files
COPY ../proto ./proto

# Build the application
RUN cargo build --release --bin reserve-service

# Runtime stage
FROM debian:bookworm-slim

# Install SSL certificates and other runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/target/release/reserve-service /app/reserve-service

# Expose the port
EXPOSE 50053

# Run the service
CMD ["./reserve-service"]