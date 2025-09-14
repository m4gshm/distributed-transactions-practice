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

	accountpb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/account"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/payment"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/database"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/payments"
)

func main() {
	cfg := config.Load("payments")

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

	// Create payment service
	paymentService := payments.NewPaymentService(db, cfg)
	accountService := payments.NewAccountService(db, cfg)

	// Setup gRPC server
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", cfg.Payments.GrpcPort))
	if err != nil {
		log.Fatalf("Failed to listen: %v", err)
	}

	s := grpc.NewServer()
	paymentpb.RegisterPaymentServiceServer(s, paymentService)
	accountpb.RegisterAccountServiceServer(s, accountService)

	// Enable reflection for grpcurl
	reflection.Register(s)

	// Graceful shutdown
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)

	go func() {
		log.Printf("Payments service listening on port %d", cfg.Payments.GrpcPort)
		if err := s.Serve(lis); err != nil {
			log.Fatalf("Failed to serve: %v", err)
		}
	}()

	<-c
	log.Println("Shutting down payments service...")
	s.GracefulStop()
}
