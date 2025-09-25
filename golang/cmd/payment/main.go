package main

import (
	"context"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/grpc"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/app"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	payments "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/impl"
)

func main() {
	name := "payment"
	cfg := config.Load().Payment

	app.Run(name, cfg,
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := payments.NewPaymentService(db)
			paymentpb.RegisterPaymentServiceServer(s, service)
			app.RegisterGateway[paymentpb.PaymentServiceServer](ctx, mux, paymentpb.RegisterPaymentServiceHandlerServer, service)
			return nil, nil
		},
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := payments.NewAccountService(db)
			paymentpb.RegisterAccountServiceServer(s, service)
			app.RegisterGateway[paymentpb.AccountServiceServer](ctx, mux, paymentpb.RegisterAccountServiceHandlerServer, service)
			return nil, nil
		},
	)
}
