package tpc

import (
	"context"
	"fmt"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/grpc"
	tpcpb "github.com/m4gshm/distributed-transactions-practice/golang/tpc/service/grpc/gen"
)

//go:generate fieldr -type Service -out . new-full

type Service struct {
	tpcpb.UnimplementedTwoPhaseCommitServiceServer
	db *pgxpool.Pool
}

func NewService(
	db *pgxpool.Pool,
) *Service {
	return &Service{
		db: db,
	}
}

func (s *Service) ListActives(ctx context.Context, req *tpcpb.TwoPhaseListActivesRequest) (*tpcpb.TwoPhaseListActivesResponse, error) {
	// Query for active prepared transactions
	rows, err := s.db.Query(ctx, `
		SELECT gid FROM pg_prepared_xacts ORDER BY gid`)
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to list active transactions: %v", err)
	}
	defer rows.Close()

	var transactions []*tpcpb.TwoPhaseListActivesResponse_Transaction
	for rows.Next() {
		var gid string
		err := rows.Scan(&gid)
		if err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to scan transaction: %v", err)
		}

		transaction := &tpcpb.TwoPhaseListActivesResponse_Transaction{
			Id: gid,
		}
		transactions = append(transactions, transaction)
	}

	return &tpcpb.TwoPhaseListActivesResponse{
		Transactions: transactions,
	}, nil
}

func (s *Service) Commit(ctx context.Context, req *tpcpb.TwoPhaseCommitRequest) (*tpcpb.TwoPhaseCommitResponse, error) {
	if req.Id == "" {
		return nil, status.Errorf(codes.InvalidArgument, "transaction ID is required")
	}

	// Check if the prepared transaction exists
	var count int
	err := s.db.QueryRow(ctx, `
		SELECT COUNT(*) FROM pg_prepared_xacts WHERE gid = $1`, req.Id).Scan(&count)
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to check transaction: %v", err)
	}

	if count == 0 {
		// Transaction doesn't exist, might have already been committed/rolled back
		return &tpcpb.TwoPhaseCommitResponse{
			Id:      req.Id,
			Message: "Transaction not found (might have already been processed)",
		}, nil
	}

	// Commit the prepared transaction
	_, err = s.db.Exec(ctx, fmt.Sprintf("COMMIT PREPARED '%s'", req.Id))
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %v", err)
	}

	return &tpcpb.TwoPhaseCommitResponse{
		Id:      req.Id,
		Message: "Transaction committed successfully",
	}, nil
}

func (s *Service) Rollback(ctx context.Context, req *tpcpb.TwoPhaseRollbackRequest) (*tpcpb.TwoPhaseRollbackResponse, error) {
	if req.Id == "" {
		return nil, status.Errorf(codes.InvalidArgument, "transaction ID is required")
	}

	// Check if the prepared transaction exists
	var count int
	err := s.db.QueryRow(ctx, `
		SELECT COUNT(*) FROM pg_prepared_xacts WHERE gid = $1`, req.Id).Scan(&count)
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to check transaction: %v", err)
	}

	if count == 0 {
		// Transaction doesn't exist, might have already been committed/rolled back
		return &tpcpb.TwoPhaseRollbackResponse{
			Id:      req.Id,
			Message: "Transaction not found (might have already been processed)",
		}, nil
	}

	// Rollback the prepared transaction
	_, err = s.db.Exec(ctx, fmt.Sprintf("ROLLBACK PREPARED '%s'", req.Id))
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to rollback transaction: %v", err)
	}

	return &tpcpb.TwoPhaseRollbackResponse{
		Id:      req.Id,
		Message: "Transaction rolled back successfully",
	}, nil
}
