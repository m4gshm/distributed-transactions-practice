package main

import (
	"context"
	"fmt"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc"

	"github.com/m4gshm/distributed-transactions-practice/golang/common/app"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/kafka/producer"
	"github.com/m4gshm/distributed-transactions-practice/golang/payment/event"
	servgrpc "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	"github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/impl"
	"github.com/m4gshm/distributed-transactions-practice/golang/payment/storage/migrations"
	tpc "github.com/m4gshm/distributed-transactions-practice/golang/tpc/service/grpc"
	tpcpb "github.com/m4gshm/distributed-transactions-practice/golang/tpc/service/grpc/gen"
)

func main() {
	name := "payment"
	cfg := config.Load().Payments

	app.Run(name, cfg.ServiceConfig, slice.Of("payment_status"),
		servgrpc.SwaggerJson, migrations.FS,
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := tpc.NewService(db)
			tpcpb.RegisterTwoPhaseCommitServiceServer(s, service)
			app.RegisterGateway[tpcpb.TwoPhaseCommitServiceServer](ctx, mux, tpcpb.RegisterTwoPhaseCommitServiceHandlerServer, service)
			return nil, nil
		},
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
