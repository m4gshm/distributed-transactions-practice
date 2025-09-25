package main

import (
	"context"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/grpc"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/app"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	reserve "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/impl"
)

func main() {
	name := "reserve"
	cfg := config.Load().Reserve

	app.Run(name, cfg,
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := reserve.NewReserveService(db)
			reservepb.RegisterReserveServiceServer(s, service)
			app.RegisterGateway[reservepb.ReserveServiceServer](ctx, mux, reservepb.RegisterReserveServiceHandlerServer, service)
			return nil, nil
		},
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := reserve.NewWarehouseService(db)
			reservepb.RegisterWarehouseItemServiceServer(s, service)
			app.RegisterGateway[reservepb.WarehouseItemServiceServer](ctx, mux, reservepb.RegisterWarehouseItemServiceHandlerServer, service)
			return nil, nil
		},
	)
}
