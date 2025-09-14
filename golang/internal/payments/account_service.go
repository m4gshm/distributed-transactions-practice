package payments

import (
	"context"
	"database/sql"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	accountpb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/account"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/models"
)

type AccountService struct {
	accountpb.UnimplementedAccountServiceServer
	db  *sql.DB
	cfg *config.Config
}

func NewAccountService(db *sql.DB, cfg *config.Config) *AccountService {
	return &AccountService{
		db:  db,
		cfg: cfg,
	}
}

func (s *AccountService) List(ctx context.Context, req *accountpb.AccountListRequest) (*accountpb.AccountListResponse, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT client_id, amount, locked, updated_at
		FROM accounts ORDER BY client_id`)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list accounts: %v", err)
	}
	defer rows.Close()

	var accounts []*accountpb.Account
	for rows.Next() {
		account := &models.Account{}
		err := rows.Scan(&account.ClientID, &account.Amount, &account.Locked, &account.UpdatedAt)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to scan account: %v", err)
		}

		accountProto := &accountpb.Account{
			ClientId:  account.ClientID,
			Amount:    account.Amount,
			Locked:    account.Locked,
			UpdatedAt: timestamppb.New(account.UpdatedAt),
		}
		accounts = append(accounts, accountProto)
	}

	return &accountpb.AccountListResponse{
		Accounts: accounts,
	}, nil
}

func (s *AccountService) TopUp(ctx context.Context, req *accountpb.AccountTopUpRequest) (*accountpb.AccountTopUpResponse, error) {
	if req.TopUp == nil {
		return nil, status.Errorf(codes.InvalidArgument, "top up data is required")
	}

	clientID := req.TopUp.ClientId
	amount := req.TopUp.Amount

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Check if account exists, create if not
	var currentAmount float64
	err = tx.QueryRowContext(ctx, `
		SELECT amount FROM accounts WHERE client_id = $1`, clientID).Scan(&currentAmount)

	if err != nil {
		if err == sql.ErrNoRows {
			// Create new account
			currentAmount = 0
			_, err = tx.ExecContext(ctx, `
				INSERT INTO accounts (client_id, amount, locked, updated_at)
				VALUES ($1, $2, 0, NOW())`, clientID, amount)
			if err != nil {
				return nil, status.Errorf(codes.Internal, "failed to create account: %v", err)
			}
		} else {
			return nil, status.Errorf(codes.Internal, "failed to get account: %v", err)
		}
	} else {
		// Update existing account
		_, err = tx.ExecContext(ctx, `
			UPDATE accounts SET amount = amount + $1, updated_at = NOW() 
			WHERE client_id = $2`, amount, clientID)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to update account: %v", err)
		}
	}

	newBalance := currentAmount + amount

	if err = tx.Commit(); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
	}

	return &accountpb.AccountTopUpResponse{
		Balance: newBalance,
	}, nil
}
