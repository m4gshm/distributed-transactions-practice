package main

import (
	"context"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	orderspb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/orders"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/database"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/orders"
)

func main() {
	cfg := config.Load("orders")

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

	// Create orders service
	ordersService := orders.NewService(db, cfg)

	// Setup gRPC server
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", cfg.Orders.GrpcPort))
	if err != nil {
		log.Fatalf("Failed to listen: %v", err)
	}

	s := grpc.NewServer()
	orderspb.RegisterOrdersServiceServer(s, ordersService)

	// Enable reflection for grpcurl
	reflection.Register(s)

	//Setup http server
	gwmux := runtime.NewServeMux()
	ctx := context.Background()
	err = orderspb.RegisterOrdersServiceHandlerServer(ctx, gwmux, ordersService)
	if err != nil {
		log.Fatalln("Failed to register gateway:", err)
	}

	gwServer := &http.Server{
		Addr:    fmt.Sprintf(":%d", cfg.Orders.HttpPort),
		Handler: gwmux,
	}

	go func() {
		log.Printf("Orders http service listening on port %d", cfg.Orders.HttpPort)
		if err := gwServer.ListenAndServe(); err != nil {
			log.Fatalf("Failed to serve: %v", err)
		}
	}()

	// Graceful shutdown
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)

	go func() {
		log.Printf("Orders grpc service listening on port %d", cfg.Orders.GrpcPort)
		if err := s.Serve(lis); err != nil {
			log.Fatalf("Failed to serve: %v", err)
		}
	}()

	<-c
	log.Println("Shutting down orders service...")
	s.GracefulStop()
	if err := gwServer.Shutdown(ctx); err != nil {
		log.Fatalf("Failed to shutdown http server: %v", err)
	}
}
