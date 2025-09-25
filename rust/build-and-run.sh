#!/bin/bash

# Build and run script for Rust services

set -e

echo "Building Rust services..."

# Build all services
cargo build --release

echo "Starting infrastructure..."

# Start PostgreSQL and Kafka using docker-compose
docker-compose up -d postgres kafka zookeeper

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to start..."
sleep 10

# Apply database schema
echo "Applying database schema..."
PGPASSWORD=postgres psql -h localhost -U postgres -d postgres -f schema.sql

echo "Services built and infrastructure started!"
echo ""
echo "To run services locally:"
echo "  Orders Service:   cargo run --bin orders-service"
echo "  Payments Service: cargo run --bin payments-service" 
echo "  Reserve Service:  cargo run --bin reserve-service"
echo "  TPC Service:      cargo run --bin tpc-service"
echo ""
echo "Or run all services with Docker:"
echo "  docker-compose up --build"
echo ""
echo "Environment variables:"
echo "  DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres"
echo "  KAFKA_BROKER=localhost:9092"