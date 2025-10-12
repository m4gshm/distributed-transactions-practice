package main

import (
	"context"
	"fmt"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/grpc"

		servgrpc "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/app"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/kafka/producer"
	"github.com/m4gshm/distributed-transactions-practice/golang/payment/event"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	"github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/impl"
	"github.com/m4gshm/gollections/slice"
)

func main() {
	name := "payment"
	cfg := config.Load().Payment

	app.Run(name, cfg.ServiceConfig, slice.Of("payment_status"),
		servgrpc.SwaggerJson,
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := impl.NewPaymentService(db)
			paymentpb.RegisterPaymentServiceServer(s, service)
			app.RegisterGateway[paymentpb.PaymentServiceServer](ctx, mux, paymentpb.RegisterPaymentServiceHandlerServer, service)
			return nil, nil
		},
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			kafka := cfg.KafkaConfig
			eventer, err := producer.New[event.AccountBalance](name, kafka.Topic, kafka.Servers)
			if err != nil {
				return nil, fmt.Errorf("failed kafka producer creating: %w", err)
			}
			service := impl.NewAccountService(db, impl.WithEventer(eventer))
			paymentpb.RegisterAccountServiceServer(s, service)
			app.RegisterGateway[paymentpb.AccountServiceServer](ctx, mux, paymentpb.RegisterAccountServiceHandlerServer, service)
			return nil, nil
		},
	)
}
