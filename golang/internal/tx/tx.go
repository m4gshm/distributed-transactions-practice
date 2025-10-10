package tx

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
)

func Opts() pgx.TxOptions {
	return pgx.TxOptions{}
}

type DB interface {
	BeginTx(ctx context.Context, txOptions pgx.TxOptions) (pgx.Tx, error)
}

func New[D DB, T any](ctx context.Context, db D, routine func(pgx.Tx) (T, error)) (no T, err error) {
	tx, err := db.BeginTx(ctx, Opts())
	if err != nil {
		return no, fmt.Errorf("failed to start transaction: %w", err)
	}
	result, err := routine(tx)
	if err == nil {
		err = tx.Commit(ctx)
	} else if rErr := tx.Rollback(ctx); rErr != nil {
		err = errors.Join(rErr, err)
	}
	return result, err
}
