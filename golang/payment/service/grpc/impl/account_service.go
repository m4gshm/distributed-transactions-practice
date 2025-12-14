package impl

import (
	"context"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/gollections/slice"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/trace"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/m4gshm/distributed-transactions-practice/golang/common/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/common/tx"
	"github.com/m4gshm/distributed-transactions-practice/golang/payment/event"
	accountpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	accountsqlc "github.com/m4gshm/distributed-transactions-practice/golang/payment/storage/sqlc/gen"
)

//go:generate fieldr -type AccountService -out . new-opt -required db

var tracerA trace.Tracer

func init() {
	tracerA = otel.Tracer("PaymentService")
}

func NewAccountService(
	db *pgxpool.Pool,
	opts ...func(*AccountService),
) *AccountService {
	r := &AccountService{
		db: db,
	}
	for _, opt := range opts {
		opt(r)
	}
	return r
}

func WithEventer(eventer AccountEventer) func(a *AccountService) {
	return func(a *AccountService) {
		a.eventer = eventer
	}
}

type AccountService struct {
	accountpb.UnimplementedAccountServiceServer
	db      *pgxpool.Pool
	eventer AccountEventer
}

type AccountServiceOpt func(*AccountService)

type AccountEventer interface {
	Send(context.Context, event.AccountBalance) error
}

func (s *AccountService) List(ctx context.Context, req *accountpb.AccountListRequest) (*accountpb.AccountListResponse, error) {
	ctx, span := tracerA.Start(ctx, "List")
	defer span.End()
	query := accountsqlc.New(s.db)
	accounts, err := query.FindAllAccounts(ctx)
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to list accounts: %v", err)
	}
	return &accountpb.AccountListResponse{Accounts: slice.Convert(accounts, toProtoAccount)}, nil
}

func (s *AccountService) TopUp(ctx context.Context, req *accountpb.AccountTopUpRequest) (*accountpb.AccountTopUpResponse, error) {
	ctx, span := tracerA.Start(ctx, "TopUp")
	defer span.End()
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
			return nil, status.Errorf(grpc.Status(err), "failed to top up account (clientID '%s'): %v", clientID, err)
		} else {
			balance := resp.Amount - resp.Locked
			if eventer := s.eventer; eventer != nil {
				e := event.AccountBalance{
					RequestID: uuid.NewString(),
					Balance:   balance,
					ClientID:  clientID,
					Timestamp: resp.UpdatedAt.Time,
				}
				if err := eventer.Send(ctx, e); err != nil {
					log.Err(err).Msgf("failed to send account event %v", e)
				} else {
					log.Debug().Msgf("account event successfully sent %v", e)
				}
			}
			return &accountpb.AccountTopUpResponse{Balance: balance}, nil
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
