package transaction

import (
	"context"

	"github.com/jackc/pgx/v5"
	"google.golang.org/grpc/status"
	
	"github.com/m4gshm/distributed-transactions-practice/golang/common/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/tpc/transaction"
)

func Prepare(ctx context.Context, tx pgx.Tx, transactionId string) error {
	if err := transaction.Prepare(ctx, tx, transactionId); err != nil {
		return status.Errorf(grpc.Status(err), "%v", err)
	}
	return nil
}
