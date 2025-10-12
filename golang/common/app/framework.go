package app

import (
	"context"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"
	"github.com/jackc/pgx/v5/tracelog"
	"github.com/m4gshm/gollections/slice"
	"github.com/pressly/goose/v3"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/grpclog"
	"google.golang.org/grpc/reflection"

	"github.com/m4gshm/distributed-transactions-practice/golang/common/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/database"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/grpczerolog"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/swagger"
)

type Close = func() error

func Run(
	name string, cfg config.ServiceConfig,
	postgresEnumTypes []string,
	swaggerJson, mirgationsFS fs.FS,
	registerServices ...func(ctx context.Context, p *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]Close, error),
) {
	ctx := context.Background()

	InitLog(zerolog.InfoLevel)

	grpcServer := NewGrpcServer()
	rmux := NewServerMux()

	migrate(ctx, cfg.Database, mirgationsFS)

	pool := NewDBPool(ctx, cfg.Database, database.WithCustomEnumType(postgresEnumTypes...))
	defer pool.Close()

	for _, buildService := range registerServices {
		closes, err := buildService(ctx, pool, grpcServer, rmux)
		for _, close := range closes {
			defer close()
		}
		if err != nil {
			log.Fatal().Err(err).Msg("Failed to register service")
		}
	}
	Start(ctx, name, cfg.GrpcPort, cfg.HttpPort, grpcServer, rmux, swaggerJson)
}

func migrate(ctx context.Context, conf config.DatabaseConfig, mirgationsFS fs.FS) {
	mpool := NewDBPool(ctx, conf)
	defer mpool.Close()
	goose.SetBaseFS(mirgationsFS)
	if err := goose.SetDialect("postgres"); err != nil {
		log.Fatal().Err(err).Msgf("Failed to register goose dialect %s", "postgres")
	}
	db := stdlib.OpenDBFromPool(mpool)
	if err := goose.Up(db, "."); err != nil {
		log.Fatal().Err(err).Msgf("Failed to migrate DB")
	} else if err := db.Close(); err != nil {
		log.Fatal().Err(err).Msgf("Failed to close migrate DB connection")
	}
}

func Start(ctx context.Context, name string, grpcPort int, httpPort int, grpcServer *grpc.Server, rmux *runtime.ServeMux, swaggerJson fs.FS) {
	lis := NewNetListener(grpcPort)
	httpServer := NewHttpServer(rmux, name, httpPort, swaggerJson)

	go func() {
		log.Info().Msgf("%s http service listening on port %d", name, httpPort)
		if err := httpServer.ListenAndServe(); err != nil {
			log.Fatal().Err(err).Msg("Failed to serve http")
		}
	}()

	go func() {
		log.Info().Msgf("%s grpc service listening on port %d", name, grpcPort)
		if err := grpcServer.Serve(lis); err != nil {
			log.Fatal().Err(err).Msg("Failed to serve grpc")
		}
	}()

	WaitTermSig()

	Shutdown(ctx, grpcServer, httpServer)
}

func NewDBPool(ctx context.Context, cfg config.DatabaseConfig, opts ...database.ConConfOpt) *pgxpool.Pool {
	db, err := database.NewPool(ctx, cfg, append(slice.Of(database.WithLogger(log.Logger, tracelog.LogLevelDebug)), opts...)...)
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to connect to database")
	}
	return db
}

func WaitTermSig() {
	termSig := make(chan os.Signal, 1)
	signal.Notify(termSig, os.Interrupt, syscall.SIGTERM)
	<-termSig
}

func Shutdown(ctx context.Context, grpcServer *grpc.Server, httpServer *http.Server) {
	log.Info().Msg("shutting down order service...")
	grpcServer.GracefulStop()
	if err := httpServer.Shutdown(ctx); err != nil {
		log.Fatal().Err(err).Msg("Failed to shutdown http server")
	}
}

func NewHttpServer(rmux *runtime.ServeMux, name string, httpPort int, swaggerJson fs.FS) *http.Server {
	mux := http.NewServeMux()
	mux.Handle("/", rmux)

	mux.HandleFunc("/swagger-ui/swagger.json", func(w http.ResponseWriter, r *http.Request) {
		http.ServeFileFS(w, r, swaggerJson, "apidocs.swagger.json")
	})

	mux.Handle("/swagger-ui/", http.StripPrefix("/swagger-ui/", http.FileServerFS(swagger.UI)))

	httpServer := &http.Server{
		Addr:    fmt.Sprintf(":%d", httpPort),
		Handler: mux,
	}
	return httpServer
}

func NewNetListener(grpcPort int) net.Listener {
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", grpcPort))
	if err != nil {
		log.Fatal().Err(err).Msg("Failed to listen")
	}
	return lis
}

func NewServerMux() *runtime.ServeMux {
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
	return rmux
}

func NewGrpcServer() *grpc.Server {
	grpcServer := grpc.NewServer()
	reflection.Register(grpcServer)
	return grpcServer
}

func NewGrpcClient(url string, name string) *grpc.ClientConn {
	paymentConn, err := grpc.NewClient(url, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatal().Err(err).Msgf("Failed to connect to %s service", name)
	}
	return paymentConn
}

func InitLog(l zerolog.Level) {
	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix
	output := zerolog.ConsoleWriter{Out: os.Stdout, TimeFormat: time.RFC3339}
	log.Logger = log.Output(output).Level(l)
	grpclog.SetLoggerV2(grpczerolog.New(log.Logger))
}

func RegisterGateway[S any](ctx context.Context, mux *runtime.ServeMux, reg func(ctx context.Context, mux *runtime.ServeMux, server S) error, service S) {
	if err := reg(ctx, mux, service); err != nil {
		log.Fatal().Err(err).Msg("Failed to register gateway")
	}
}
