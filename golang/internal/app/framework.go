package app

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/tracelog"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/database"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpczerolog"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/grpclog"
	"google.golang.org/grpc/reflection"
)

func Run(
	name string, cfg config.ServiceConfig,
	registerServices ...func(ctx context.Context, p *pgxpool.Pool, s grpc.ServiceRegistrar, mux *runtime.ServeMux) ([]func() error, error),
) {
	ctx := context.Background()

	InitLog()

	grpcServer := NewGrpcServer()
	rmux := NewServerMux()

	db := NewDB(ctx, cfg.Database)
	defer db.Close()

	for _, buildService := range registerServices {
		closes, err := buildService(ctx, db, grpcServer, rmux)
		if err != nil {
			log.Fatal().Err(err).Msg("Failed to register service")
		}
		for _, close := range closes {
			defer close()
		}
	}
	Start(ctx, name, cfg.GrpcPort, cfg.HttpPort, grpcServer, rmux)
}

func Start(ctx context.Context, name string, grpcPort int, httpPort int, grpcServer *grpc.Server, rmux *runtime.ServeMux) {
	lis := NewNetListener(grpcPort)
	httpServer := NewHttpServer(rmux, name, httpPort)

	go func() {
		log.Printf("%s http service listening on port %d", name, httpPort)
		if err := httpServer.ListenAndServe(); err != nil {
			log.Fatal().Err(err).Msg("Failed to serve")
		}
	}()

	go func() {
		log.Info().Msgf("%s grpc service listening on port %d", name, grpcPort)
		if err := grpcServer.Serve(lis); err != nil {
			log.Fatal().Err(err).Msg("Failed to serve")
		}
	}()

	WaitTermSig()

	Shutdown(ctx, grpcServer, httpServer)
}

func NewDB(ctx context.Context, cfg config.DatabaseConfig) *pgxpool.Pool {
	db, err := database.NewConnection(ctx, cfg, database.WithLogger(log.Logger, tracelog.LogLevelDebug))
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

func NewHttpServer(rmux *runtime.ServeMux, name string, httpPort int) *http.Server {
	mux := http.NewServeMux()
	mux.Handle("/", rmux)

	mux.HandleFunc("/swagger-ui/swagger.json", func(w http.ResponseWriter, r *http.Request) {
		http.ServeFile(w, r, fmt.Sprintf("../../%s/service/grpc/gen/apidocs.swagger.json", name))
	})

	mux.Handle("/swagger-ui/", http.StripPrefix("/swagger-ui/", http.FileServer(http.Dir("../../../swagger-ui/5.29.0/"))))

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

func InitLog() {
	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix
	output := zerolog.ConsoleWriter{Out: os.Stdout, TimeFormat: time.RFC3339}
	log.Logger = log.Output(output).Level(zerolog.DebugLevel)
	grpclog.SetLoggerV2(grpczerolog.New(log.Logger))
}

func RegisterGateway[S any](ctx context.Context, mux *runtime.ServeMux, reg func(ctx context.Context, mux *runtime.ServeMux, server S) error, service S) {
	if err := reg(ctx, mux, service); err != nil {
		log.Fatal().Err(err).Msg("Failed to register gateway")
	}
}
