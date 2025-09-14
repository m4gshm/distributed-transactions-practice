package main

import (
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"

	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"

	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/reserve"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/database"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/reserve"
)

func main() {
	cfg := config.Load("reserve")

	// Setup database connection
	db, err := database.NewConnection(cfg.Database)
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer db.Close()

	// Run database migrations
	err = database.RunMigrations(db, "./migrations")
	if err != nil {
		log.Fatalf("Failed to run migrations: %v", err)
	}

	// Create reserve services
	reserveService := reserve.NewReserveService(db, cfg)
	warehouseService := reserve.NewWarehouseService(db, cfg)

	// Setup gRPC server
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", cfg.Reserve.GrpcPort))
	if err != nil {
		log.Fatalf("Failed to listen: %v", err)
	}

	s := grpc.NewServer()
	reservepb.RegisterReserveServiceServer(s, reserveService)
	reservepb.RegisterWarehouseItemServiceServer(s, warehouseService)

	// Enable reflection for grpcurl
	reflection.Register(s)

	// Graceful shutdown
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)

	go func() {
		log.Printf("Reserve service listening on port %d", cfg.Reserve.GrpcPort)
		if err := s.Serve(lis); err != nil {
			log.Fatalf("Failed to serve: %v", err)
		}
	}()

	<-c
	log.Println("Shutting down reserve service...")
	s.GracefulStop()
}
