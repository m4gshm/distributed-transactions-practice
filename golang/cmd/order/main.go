package main

import (
	"context"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc"

	"github.com/m4gshm/distributed-transactions-practice/golang/common/app"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/order/service"
	servgrpc "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc"
	orderpb "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/gen"
	order "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/impl"
	"github.com/m4gshm/distributed-transactions-practice/golang/order/storage/migrations"
	payment "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	reserve "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	tpc "github.com/m4gshm/distributed-transactions-practice/golang/tpc/service/grpc/gen"
)

func main() {
	name := "orders"
	cfg := config.Load().Order

	app.Run(name, cfg.ServiceConfig, slice.Of("order_status", "delivery_type"),
		servgrpc.SwaggerJson, migrations.FS,
		func(ctx context.Context, db *pgxpool.Pool, reg grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]app.Close, error) {
			paymentConn := app.NewGrpcClient(cfg.PaymentServiceURL, "payment")
			reserveConn := app.NewGrpcClient(cfg.ReserveServiceURL, "reserve")
			closes := slice.Of(paymentConn.Close, reserveConn.Close)

			payClient := payment.NewPaymentServiceClient(paymentConn)
			payTpcClient := tpc.NewTwoPhaseCommitServiceClient(paymentConn)
			resClient := reserve.NewReserveServiceClient(reserveConn)
			resTpcClient := tpc.NewTwoPhaseCommitServiceClient(reserveConn)
			whouseClient := reserve.NewWarehouseItemServiceClient(reserveConn)

			ordServ := order.NewOrderService(db, payClient, payTpcClient, resClient, resTpcClient, whouseClient)

			orderpb.RegisterOrderServiceServer(reg, ordServ)
			app.RegisterGateway[orderpb.OrderServiceServer](ctx, mux, orderpb.RegisterOrderServiceHandlerServer, ordServ)

			kafka := cfg.KafkaConfig

			err := service.NewAccountEventListener(name, slice.Of(kafka.Topic), kafka.Servers, db, ordServ, payClient, false).Listen(ctx)

			return closes, err
		})
}
