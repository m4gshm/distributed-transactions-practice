package main

import (
	"context"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc"

	"github.com/m4gshm/distributed-transactions-practice/golang/common/app"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/config"
	servgrpc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc"
	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	reserve "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/impl"
	"github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/migrations"
	ressqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/reserve/sqlc/gen"
	whsqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/warehouse/sqlc/gen"
	tpc "github.com/m4gshm/distributed-transactions-practice/golang/tpc/service/grpc"
	tpcpb "github.com/m4gshm/distributed-transactions-practice/golang/tpc/service/grpc/gen"
)

func main() {
	name := "reserve"
	cfg := config.Load().Reserve

	app.Run(name, cfg, slice.Of("reserve_status"),
		servgrpc.SwaggerJson, migrations.FS,
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := tpc.NewService(db)
			tpcpb.RegisterTwoPhaseCommitServiceServer(s, service)
			app.RegisterGateway[tpcpb.TwoPhaseCommitServiceServer](ctx, mux, tpcpb.RegisterTwoPhaseCommitServiceHandlerServer, service)
			return nil, nil
		},
		func(ctx context.Context, db *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error) {
			service := reserve.NewReserveService(db, ressqlc.New, whsqlc.New)
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
