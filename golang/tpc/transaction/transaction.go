package transaction

import (
	"context"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
)

func Prepare(ctx context.Context, tx pgx.Tx, transactionId string) error {
	if _, err := tx.Exec(ctx, fmt.Sprintf("PREPARE TRANSACTION '%s'", strings.ReplaceAll(transactionId, ";", ""))); err != nil {
		return fmt.Errorf("failed to prepare transaction %s: %v", transactionId, err)
	}
	return nil
}
