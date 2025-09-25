package database

import (
	"context"
	"fmt"

	pgxZerolog "github.com/jackc/pgx-zerolog"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/tracelog"
	_ "github.com/lib/pq"
	"github.com/rs/zerolog"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
)

type ConConfOpt func(*pgxpool.Config) error

func NewConnection(ctx context.Context, cfg config.DatabaseConfig, opts ...ConConfOpt) (*pgxpool.Pool, error) {
	dsn := fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=%s",
		cfg.Host, cfg.Port, cfg.User, cfg.Password, cfg.DBName, cfg.SSLMode)

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

func WithLogger(log zerolog.Logger, logLevel tracelog.LogLevel) ConConfOpt {
	return func(c *pgxpool.Config) error {
		c.ConnConfig.Tracer = &tracelog.TraceLog{
			Logger:   pgxZerolog.NewLogger(log),
			LogLevel: logLevel,
		}
		return nil
	}
}
