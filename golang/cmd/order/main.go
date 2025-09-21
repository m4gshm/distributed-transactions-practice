package main

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/grpclog"
	"google.golang.org/grpc/reflection"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/database"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpczerolog"
	orderpb "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/gen"
	order "github.com/m4gshm/distributed-transactions-practice/golang/order/service/impl"
	payment "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	reserve "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	warehouse "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
)

func main() {
	ctx := context.Background()

	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix

	output := zerolog.ConsoleWriter{Out: os.Stdout, TimeFormat: time.RFC3339}
	log.Logger = log.Output(output)
	grpclog.SetLoggerV2(grpczerolog.New(log.Logger))

	cfg := config.Load().Order

	// Setup database connection
	db, err := database.NewConnection(ctx, cfg.Database)
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to connect to database")
	}
	defer db.Close()

	// Run database migrations
	// err = database.RunMigrations(ctx, db, "./migrations")
	// if err != nil {
	// 	log.Fatal().Err(err).Msg("Failed to run migrations")
	// }

	// Create order service

	paymentConn, err := grpc.NewClient(cfg.PaymentServiceURL, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to connect to payment service")
	}
	defer paymentConn.Close()

	reserveConn, err := grpc.NewClient(cfg.ReserveServiceURL, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to connect to reserve service")
	}
	defer reserveConn.Close()

	orderService := order.NewService(
		db,
		payment.NewPaymentServiceClient(paymentConn),
		reserve.NewReserveServiceClient(reserveConn),
		warehouse.NewWarehouseItemServiceClient(reserveConn),
	)

	// Setup gRPC server
	grpcPort := cfg.GrpcPort
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", grpcPort))
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to listen")
	}

	grpcServer := grpc.NewServer()
	orderpb.RegisterOrderServiceServer(grpcServer, orderService)

	// Enable reflection for grpcurl
	reflection.Register(grpcServer)

	//Setup http server
	rmux := runtime.NewServeMux(runtime.WithErrorHandler(func(
		ctx context.Context,
		sm *runtime.ServeMux,
		m runtime.Marshaler,
		w http.ResponseWriter,
		r *http.Request,
		err error,
	) {
		log.Err(err).Msgf("request path %s", r.URL.Path)
		runtime.DefaultHTTPErrorHandler(ctx, sm, m, w, r, err)
	}))
	err = orderpb.RegisterOrderServiceHandlerServer(ctx, rmux, orderService)
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to register gateway")
	}

	mux := http.NewServeMux()
	mux.Handle("/", rmux)

	mux.HandleFunc("/swagger-ui/swagger.json", func(w http.ResponseWriter, r *http.Request) {
		http.ServeFile(w, r, "../../order/service/grpc/gen/apidocs.swagger.json")
	})

	mux.Handle("/swagger-ui/", http.StripPrefix("/swagger-ui/", http.FileServer(http.Dir("../../../swagger-ui/5.29.0/"))))

	httpPort := cfg.HttpPort
	httpServer := &http.Server{
		Addr:    fmt.Sprintf(":%d", httpPort),
		Handler: mux,
	}

	go func() {
		log.Printf("Orders http service listening on port %d", httpPort)
		if err := httpServer.ListenAndServe(); err != nil {
			log.Fatal().Err(err).Msg("Failed to serve")
		}
	}()

	// Graceful shutdown
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)

	go func() {
		log.Info().Msgf("Orders grpc service listening on port %d", grpcPort)
		if err := grpcServer.Serve(lis); err != nil {
			log.Fatal().Err(err).Msg("Failed to serve")
		}
	}()

	<-c
	log.Info().Msg("Shutting down order service...")
	grpcServer.GracefulStop()
	if err := httpServer.Shutdown(ctx); err != nil {
		log.Fatal().Err(err).Msg("Failed to shutdown http server")
	}
}
