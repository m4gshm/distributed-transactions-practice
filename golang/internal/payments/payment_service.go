package payments

import (
	"context"
	"database/sql"

	"github.com/google/uuid"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/payment"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/models"
)

type PaymentService struct {
	paymentpb.UnimplementedPaymentServiceServer
	db  *sql.DB
	cfg *config.Config
}

func NewPaymentService(db *sql.DB, cfg *config.Config) *PaymentService {
	return &PaymentService{
		db:  db,
		cfg: cfg,
	}
}

func (s *PaymentService) Create(ctx context.Context, req *paymentpb.PaymentCreateRequest) (*paymentpb.PaymentCreateResponse, error) {
	if req.Body == nil {
		return nil, status.Errorf(codes.InvalidArgument, "payment body is required")
	}

	paymentID := uuid.New().String()

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Check if prepared transaction ID is provided for 2PC
	if req.PreparedTransactionId != nil {
		// Handle prepared transaction logic
		_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to prepare transaction: %v", err)
		}
	}

	// Insert payment
	_, err = tx.ExecContext(ctx, `
		INSERT INTO payments (id, external_ref, client_id, amount, status, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, NOW(), NOW())`,
		paymentID,
		req.Body.ExternalRef,
		req.Body.ClientId,
		req.Body.Amount,
		int(models.PaymentStatusCreated),
	)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to create payment: %v", err)
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &paymentpb.PaymentCreateResponse{
		Id: paymentID,
	}, nil
}

func (s *PaymentService) Approve(ctx context.Context, req *paymentpb.PaymentApproveRequest) (*paymentpb.PaymentApproveResponse, error) {
	// Get payment
	payment, err := s.getPaymentByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Check if payment can be approved
	if payment.Status != models.PaymentStatusCreated && payment.Status != models.PaymentStatusInsufficient {
		return nil, status.Errorf(codes.FailedPrecondition, "payment cannot be approved in current status")
	}

	// Get account
	account, err := s.getAccountByClientID(ctx, payment.ClientID)
	if err != nil {
		return nil, err
	}

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Check if prepared transaction ID is provided for 2PC
	if req.PreparedTransactionId != nil {
		_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to prepare transaction: %v", err)
		}
	}

	// Check if account has sufficient funds
	newStatus := models.PaymentStatusHold
	insufficientAmount := 0.0

	if account.Amount-account.Locked < payment.Amount {
		newStatus = models.PaymentStatusInsufficient
		insufficientAmount = payment.Amount - (account.Amount - account.Locked)
	} else {
		// Lock the amount
		_, err = tx.ExecContext(ctx, `
			UPDATE accounts SET locked = locked + $1, updated_at = NOW() 
			WHERE client_id = $2`, payment.Amount, payment.ClientID)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to lock funds: %v", err)
		}
	}

	// Update payment status
	_, err = tx.ExecContext(ctx, `
		UPDATE payments SET status = $1, insufficient = $2, updated_at = NOW() 
		WHERE id = $3`, int(newStatus),
		func() interface{} {
			if insufficientAmount > 0 {
				return insufficientAmount
			}
			return nil
		}(), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update payment: %v", err)
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &paymentpb.PaymentApproveResponse{
		Id:                 req.Id,
		Status:             paymentpb.Payment_Status(newStatus),
		InsufficientAmount: insufficientAmount,
	}, nil
}

func (s *PaymentService) Cancel(ctx context.Context, req *paymentpb.PaymentCancelRequest) (*paymentpb.PaymentCancelResponse, error) {
	// Get payment
	payment, err := s.getPaymentByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Check if prepared transaction ID is provided for 2PC
	if req.PreparedTransactionId != nil {
		_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to prepare transaction: %v", err)
		}
	}

	// If payment was in HOLD status, unlock the funds
	if payment.Status == models.PaymentStatusHold {
		_, err = tx.ExecContext(ctx, `
			UPDATE accounts SET locked = locked - $1, updated_at = NOW() 
			WHERE client_id = $2`, payment.Amount, payment.ClientID)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to unlock funds: %v", err)
		}
	}

	// Update payment status
	_, err = tx.ExecContext(ctx, `
		UPDATE payments SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.PaymentStatusCancelled), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to cancel payment: %v", err)
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &paymentpb.PaymentCancelResponse{
		Id:     req.Id,
		Status: paymentpb.Payment_Status(models.PaymentStatusCancelled),
	}, nil
}

func (s *PaymentService) Pay(ctx context.Context, req *paymentpb.PaymentPayRequest) (*paymentpb.PaymentPayResponse, error) {
	// Get payment
	payment, err := s.getPaymentByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Check if payment can be paid
	if payment.Status != models.PaymentStatusHold {
		return nil, status.Errorf(codes.FailedPrecondition, "payment cannot be paid in current status")
	}

	// Get account
	account, err := s.getAccountByClientID(ctx, payment.ClientID)
	if err != nil {
		return nil, err
	}

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Check if prepared transaction ID is provided for 2PC
	if req.PreparedTransactionId != nil {
		_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to prepare transaction: %v", err)
		}
	}

	// Deduct amount from account and unlock
	newBalance := account.Amount - payment.Amount
	_, err = tx.ExecContext(ctx, `
		UPDATE accounts SET amount = $1, locked = locked - $2, updated_at = NOW() 
		WHERE client_id = $3`, newBalance, payment.Amount, payment.ClientID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to deduct payment: %v", err)
	}

	// Update payment status
	_, err = tx.ExecContext(ctx, `
		UPDATE payments SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.PaymentStatusPaid), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update payment: %v", err)
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &paymentpb.PaymentPayResponse{
		Id:      req.Id,
		Status:  paymentpb.Payment_Status(models.PaymentStatusPaid),
		Balance: newBalance,
	}, nil
}

func (s *PaymentService) Get(ctx context.Context, req *paymentpb.PaymentGetRequest) (*paymentpb.PaymentGetResponse, error) {
	payment, err := s.getPaymentByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	paymentProto := s.paymentToProto(payment)
	return &paymentpb.PaymentGetResponse{
		Payment: paymentProto,
	}, nil
}

func (s *PaymentService) List(ctx context.Context, req *paymentpb.PaymentListRequest) (*paymentpb.PaymentListResponse, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, external_ref, client_id, amount, insufficient, status, created_at, updated_at
		FROM payments ORDER BY created_at DESC`)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list payments: %v", err)
	}
	defer rows.Close()

	var payments []*paymentpb.Payment
	for rows.Next() {
		payment := &models.Payment{}
		err := rows.Scan(
			&payment.ID, &payment.ExternalRef, &payment.ClientID, &payment.Amount,
			&payment.Insufficient, &payment.Status, &payment.CreatedAt, &payment.UpdatedAt,
		)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to scan payment: %v", err)
		}

		paymentProto := s.paymentToProto(payment)
		payments = append(payments, paymentProto)
	}

	return &paymentpb.PaymentListResponse{
		Payments: payments,
	}, nil
}

func (s *PaymentService) getPaymentByID(ctx context.Context, paymentID string) (*models.Payment, error) {
	payment := &models.Payment{}
	err := s.db.QueryRowContext(ctx, `
		SELECT id, external_ref, client_id, amount, insufficient, status, created_at, updated_at
		FROM payments WHERE id = $1`, paymentID).Scan(
		&payment.ID, &payment.ExternalRef, &payment.ClientID, &payment.Amount,
		&payment.Insufficient, &payment.Status, &payment.CreatedAt, &payment.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, status.Errorf(codes.NotFound, "payment not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to get payment: %v", err)
	}
	return payment, nil
}

func (s *PaymentService) getAccountByClientID(ctx context.Context, clientID string) (*models.Account, error) {
	account := &models.Account{}
	err := s.db.QueryRowContext(ctx, `
		SELECT client_id, amount, locked, updated_at
		FROM accounts WHERE client_id = $1`, clientID).Scan(
		&account.ClientID, &account.Amount, &account.Locked, &account.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, status.Errorf(codes.NotFound, "account not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to get account: %v", err)
	}
	return account, nil
}

func (s *PaymentService) paymentToProto(payment *models.Payment) *paymentpb.Payment {
	paymentProto := &paymentpb.Payment{
		ExternalRef: payment.ExternalRef,
		ClientId:    payment.ClientID,
		Amount:      payment.Amount,
		Status:      paymentpb.Payment_Status(payment.Status),
	}

	if payment.Insufficient != nil {
		paymentProto.Insufficient = payment.Insufficient
	}

	return paymentProto
}
