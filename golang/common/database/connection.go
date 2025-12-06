package database

import (
	"context"
	"fmt"

	pgxZerolog "github.com/jackc/pgx-zerolog"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/tracelog"
	_ "github.com/lib/pq"
	"github.com/m4gshm/expressions/expr/get"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"github.com/m4gshm/distributed-transactions-practice/golang/common/config"
)

type ConConfOpt func(*pgxpool.Config) error

func NewPool(ctx context.Context, cfg config.DatabaseConfig, opts ...ConConfOpt) (*pgxpool.Pool, error) {
	dsn := fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=%s",
		cfg.Host, cfg.Port, cfg.User, cfg.Password, cfg.DBName, cfg.SSLMode)

	log.Info().Msgf("DB connection host=%s port=%d user=%s password=%s dbname=%s sslmode=%s",
		cfg.Host, cfg.Port, cfg.User, "*****", cfg.DBName, cfg.SSLMode)

	config, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("unable to parse connection string '%s': %w", dsn, err)
	}

	for _, opt := range opts {
		if err := opt(config); err != nil {
			return nil, fmt.Errorf("unable to apply option arg to connection string '%s': %w", dsn, err)
		}
	}
	dbpool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		return nil, fmt.Errorf("unable to create connection pool: %w", err)
	} else if err = dbpool.Ping(ctx); err != nil {
		dbpool.Close()
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}
	return dbpool, nil
}

func RegfisterType(ctx context.Context, conn *pgx.Conn, enumType string) error {
	dataType, err := conn.LoadType(ctx, enumType)
	if err != nil {
		return fmt.Errorf("failed to load type '%s': %w", enumType, err)
	}
	conn.TypeMap().RegisterType(dataType)

	arrayType := "_" + enumType
	arrayDataType, err := conn.LoadType(ctx, arrayType)
	if err != nil {
		return fmt.Errorf("failed to load type '%s': %w", arrayType, err)
	}
	conn.TypeMap().RegisterType(arrayDataType)
	return nil
}

func WithCustomEnumType(types ...string) ConConfOpt {
	return func(c *pgxpool.Config) error {
		prev := c.AfterConnect
		c.AfterConnect = func(ctx context.Context, conn *pgx.Conn) error {
			for _, typ := range types {
				ere := RegfisterType(ctx, conn, typ)
				if ere != nil {
					return ere
				}
			}
			if prev != nil {
				return prev(ctx, conn)
			}
			return nil
		}
		return nil
	}
}

func NewTraceLog(log zerolog.Logger, logLevel tracelog.LogLevel) *tracelog.TraceLog {
	return &tracelog.TraceLog{
		Logger:   pgxZerolog.NewLogger(log),
		LogLevel: logLevel,
	}
}

func WithTracers(tracers ...pgx.QueryTracer) ConConfOpt {
	t := get.If(len(tracers) == 1, func() pgx.QueryTracer { return tracers[0] }).Else(aggerate(tracers))
	return func(c *pgxpool.Config) error {
		c.ConnConfig.Tracer = t
		return nil
	}
}

type aggerate []pgx.QueryTracer

func (a aggerate) TraceQueryStart(ctx context.Context, conn *pgx.Conn, data pgx.TraceQueryStartData) context.Context {
	for _, t := range a {
		ctx = t.TraceQueryStart(ctx, conn, data)
	}
	return ctx
}

func (a aggerate) TraceQueryEnd(ctx context.Context, conn *pgx.Conn, data pgx.TraceQueryEndData) {
	for _, t := range a {
		t.TraceQueryEnd(ctx, conn, data)
	}
}
