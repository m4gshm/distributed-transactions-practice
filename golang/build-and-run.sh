#!/bin/bash

# Build and run the distributed transactions practice services in Go

echo "Building Go services..."

# Build all services
echo "Building orders service..."
go build -o bin/orders ./cmd/orders

echo "Building payments service..."
go build -o bin/payments ./cmd/payments

echo "Building reserve service..."
go build -o bin/reserve ./cmd/reserve

echo "Building tpc service..."
go build -o bin/tpc ./cmd/tpc

echo "All services built successfully!"

# Create directory for binaries if it doesn't exist
mkdir -p bin

echo "To run the services:"
echo "1. Start PostgreSQL database (or use docker-compose up postgres)"
echo "2. Run each service:"
echo "   ./bin/orders"
echo "   ./bin/payments"
echo "   ./bin/reserve"
echo "   ./bin/tpc"
echo ""
echo "Or use Docker Compose:"
echo "   docker-compose up --build"