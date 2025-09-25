package service

import (
	"context"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/tx"
	accountpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	accountsqlc "github.com/m4gshm/distributed-transactions-practice/golang/payment/storage/sqlc/gen"
)

type AccountService struct {
	accountpb.UnimplementedAccountServiceServer
	db *pgxpool.Pool
}

func NewAccountService(db *pgxpool.Pool) *AccountService {
	return &AccountService{db: db}
}

func (s *AccountService) List(ctx context.Context, req *accountpb.AccountListRequest) (*accountpb.AccountListResponse, error) {
	query := accountsqlc.New(s.db)
	accounts, err := query.FindAllAccounts(ctx)
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to list accounts: %v", err)
	}
	return &accountpb.AccountListResponse{Accounts: slice.Convert(accounts, toProtoAccount)}, nil
}

func (s *AccountService) TopUp(ctx context.Context, req *accountpb.AccountTopUpRequest) (*accountpb.AccountTopUpResponse, error) {
	topUp := req.TopUp
	if topUp == nil {
		return nil, status.Errorf(codes.InvalidArgument, "top up data is required")
	}
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*accountpb.AccountTopUpResponse, error) {
		query := accountsqlc.New(tx)
		clientID := topUp.ClientId
		if resp, err := query.AddAmount(ctx, accountsqlc.AddAmountParams{
			ClientID: clientID,
			Amount:   topUp.Amount,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to top up account (clientID [%s]): %v", clientID, err)
		} else {
			return &accountpb.AccountTopUpResponse{Balance: resp.Amount - resp.Locked}, nil
		}
	})
}

func toProtoAccount(account accountsqlc.Account) *accountpb.Account {
	return &accountpb.Account{
		ClientId:  account.ClientID,
		Amount:    account.Amount,
		Locked:    account.Locked,
		UpdatedAt: timestamppb.New(account.UpdatedAt.Time),
	}
}
