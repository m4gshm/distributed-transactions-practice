package main

import (
	"context"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/app"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	orderpb "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/gen"
	order "github.com/m4gshm/distributed-transactions-practice/golang/order/service/impl"
	payment "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	reserve "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	warehouse "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
)

func main() {
	name := "order"
	cfg := config.Load().Order

	app.Run(name, cfg.ServiceConfig, func(ctx context.Context, p *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
		service, closes := NewOrderService(cfg, p)
		orderpb.RegisterOrderServiceServer(s, service)
		app.RegisterGateway[orderpb.OrderServiceServer](ctx, mux, orderpb.RegisterOrderServiceHandlerServer, service)
		return closes, nil
	})
}

func NewOrderService(cfg config.OrderConfig, db *pgxpool.Pool) (*order.OrderService, []func() error) {
	paymentConn := app.NewGrpcClient(cfg.PaymentServiceURL, "payment")
	reserveConn := app.NewGrpcClient(cfg.ReserveServiceURL, "reserve")
	closes := slice.Of(paymentConn.Close, reserveConn.Close)

	orderService := order.NewService(
		db,
		payment.NewPaymentServiceClient(paymentConn),
		reserve.NewReserveServiceClient(reserveConn),
		warehouse.NewWarehouseItemServiceClient(reserveConn),
	)
	return orderService, closes
}
