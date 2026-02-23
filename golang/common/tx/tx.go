package tx

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/trace"
)

var tracer trace.Tracer

func init() {
	tracer = otel.Tracer("tx")
}

func Opts() pgx.TxOptions {
	return pgx.TxOptions{}
}

type DB interface {
	BeginTx(ctx context.Context, txOptions pgx.TxOptions) (pgx.Tx, error)
}

func New[D DB, T any](ctx context.Context, db D, routine func(context.Context, pgx.Tx) (T, error)) (no T, err error) {
	ctx, span := tracer.Start(ctx, "tranaction")
	defer span.End()
	tx, err := db.BeginTx(ctx, Opts())
	if err != nil {
		span.RecordError(err)
		return no, fmt.Errorf("failed to start transaction: %w", err)
	}
	result, err := routine(ctx, tx)
	if err == nil {
		err = tx.Commit(ctx)
	} else if rErr := tx.Rollback(ctx); rErr != nil {
		err = errors.Join(rErr, err)
		span.RecordError(err)
	}
	return result, err
}
