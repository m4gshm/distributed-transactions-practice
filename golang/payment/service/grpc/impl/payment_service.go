package impl

import (
	"context"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/pg"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/tx"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	paymentsqlc "github.com/m4gshm/distributed-transactions-practice/golang/payment/storage/sqlc/gen"
	"github.com/m4gshm/gollections/op"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"time"
)

//go:generate fieldr -type PaymentService -out . new-full

type PaymentService struct {
	paymentpb.UnimplementedPaymentServiceServer
	db *pgxpool.Pool
}

func NewPaymentService(
	db *pgxpool.Pool,
) *PaymentService {
	return &PaymentService{
		db: db,
	}
}

func (s *PaymentService) Create(ctx context.Context, req *paymentpb.PaymentCreateRequest) (*paymentpb.PaymentCreateResponse, error) {
	body := req.Body
	if body == nil {
		return nil, status.Errorf(codes.InvalidArgument, "payment body is required")
	}
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*paymentpb.PaymentCreateResponse, error) {
		paymentID := uuid.New().String()

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	// Handle prepared transaction logic
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %v", err)
		// 	}
		// }

		query := paymentsqlc.New(tx)
		if err := query.UpsertPayment(ctx, paymentsqlc.UpsertPaymentParams{
			ID:          paymentID,
			CreatedAt:   pg.Timestamptz(time.Now()),
			ExternalRef: body.ExternalRef,
			ClientID:    body.ClientId,
			Status:      paymentsqlc.PaymentStatusCREATED,
			Amount:      body.Amount,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to create payment: %v", err)
		}

		// if req.PreparedTransactionId == nil {
		// 	if err = tx.Commit(ctx); err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %v", err)
		// 	}
		// }

		return &paymentpb.PaymentCreateResponse{Id: paymentID}, nil
	})
}

func (s *PaymentService) Approve(ctx context.Context, req *paymentpb.PaymentApproveRequest) (*paymentpb.PaymentApproveResponse, error) {
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*paymentpb.PaymentApproveResponse, error) {
		query := paymentsqlc.New(tx)

		payment, err := query.FindPaymentByID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		if s := payment.Status; s != paymentsqlc.PaymentStatusCREATED && s != paymentsqlc.PaymentStatusINSUFFICIENT {
			return nil, status.Errorf(codes.FailedPrecondition, "payment cannot be approved in current status '%s'", s)
		}

		account, err := query.FindAccountByIdForUpdate(ctx, payment.ClientID)
		if err != nil {
			return nil, err
		}

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %v", err)
		// 	}
		// }

		// Check if account has sufficient funds
		insufficientAmount := 0.0
		if account.Amount-account.Locked < payment.Amount {
			insufficientAmount = payment.Amount - (account.Amount - account.Locked)
			// return &paymentpb.PaymentApproveResponse{
			// 	Id: req.Id,
			// 	Status: paymentpb.Payment_INSUFFICIENT,
			// 	InsufficientAmount: insufficientAmount,
			// }, nil
		} else if _, err := query.AddLock(ctx, paymentsqlc.AddLockParams{
			ClientID: payment.ClientID,
			Locked:   payment.Amount,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to lock funds: %v", err)
		}

		paymentStatus := op.IfElse(insufficientAmount > 0.0,
			paymentsqlc.PaymentStatusINSUFFICIENT,
			paymentsqlc.PaymentStatusHOLD,
		)

		if err := query.UpdatePaymentStatus(ctx, paymentsqlc.UpdatePaymentStatusParams{
			ID:           req.Id,
			Insufficient: &insufficientAmount,
			Status:       paymentStatus,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to update payment: %v", err)
		}

		// if req.PreparedTransactionId == nil {
		// 	if err = tx.Commit(ctx); err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %v", err)
		// 	}
		// }
		return &paymentpb.PaymentApproveResponse{
			Id:                 req.Id,
			Status:             toProtoPaymentStatus(paymentStatus),
			InsufficientAmount: insufficientAmount,
		}, nil
	})
}

func (s *PaymentService) Cancel(ctx context.Context, req *paymentpb.PaymentCancelRequest) (*paymentpb.PaymentCancelResponse, error) {
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*paymentpb.PaymentCancelResponse, error) {
		query := paymentsqlc.New(tx)

		payment, err := query.FindPaymentByID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %v", err)
		// 	}
		// }

		// If payment was in HOLD status, unlock the funds
		if payment.Status == paymentsqlc.PaymentStatusHOLD {
			if _, err := query.Unlock(ctx, paymentsqlc.UnlockParams{
				ClientID: payment.ClientID,
				Locked:   payment.Amount,
			}); err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to unlock funds: %v", err)
			}
		}

		newStatus := paymentsqlc.PaymentStatusCANCELLED
		if err := query.UpdatePaymentStatus(ctx, paymentsqlc.UpdatePaymentStatusParams{
			ID:     payment.ID,
			Status: newStatus,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to cancel payment: %v", err)
		}

		// if req.PreparedTransactionId == nil {
		// 	if err = tx.Commit(); err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %v", err)
		// 	}
		// }

		return &paymentpb.PaymentCancelResponse{
			Id:     payment.ID,
			Status: toProtoPaymentStatus(newStatus),
		}, nil
	})
}

func (s *PaymentService) Pay(ctx context.Context, req *paymentpb.PaymentPayRequest) (*paymentpb.PaymentPayResponse, error) {
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*paymentpb.PaymentPayResponse, error) {
		query := paymentsqlc.New(tx)

		payment, err := query.FindPaymentByID(ctx, req.Id)
		if err != nil {
			return nil, err
		}
		// Check if payment can be paid
		if payment.Status != paymentsqlc.PaymentStatusHOLD {
			return nil, status.Errorf(codes.FailedPrecondition, "payment cannot be paid in current status '%s'", payment.Status)
		}

		account, err := query.FindAccountByIdForUpdate(ctx, payment.ClientID)
		_ = account
		if err != nil {
			return nil, err
		}

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %v", err)
		// 	}
		// }

		const finalStatus = paymentsqlc.PaymentStatusPAID
		if account, err := query.WriteOff(ctx, paymentsqlc.WriteOffParams{
			ClientID: payment.ClientID,
			Amount:   payment.Amount,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to write off account: clientId '%s': %v", payment.ClientID, err)
		} else if account.Amount < 0 || account.Locked < 0 {
			return nil, status.Errorf(codes.OutOfRange, "incorrect write off account: clientId '%s', newAmount '%f', newLocked '%f': %v",
				payment.ClientID, account.Amount, account.Locked, err)
		} else if err := query.UpdatePaymentStatus(ctx, paymentsqlc.UpdatePaymentStatusParams{
			ID:        payment.ID,
			Status:    finalStatus,
			UpdatedAt: pg.Timestamptz(time.Now()),
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to update payment: %v", err)
		} else {
			// if req.PreparedTransactionId == nil {
			// 	if err = tx.Commit(); err != nil {
			// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %v", err)
			// 	}
			// }
			return &paymentpb.PaymentPayResponse{
				Id:      req.Id,
				Status:  toProtoPaymentStatus(finalStatus),
				Balance: account.Amount - account.Locked,
			}, nil
		}
	})
}

func (s *PaymentService) Get(ctx context.Context, req *paymentpb.PaymentGetRequest) (*paymentpb.PaymentGetResponse, error) {
	query := paymentsqlc.New(s.db)
	payment, err := query.FindPaymentByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}
	return &paymentpb.PaymentGetResponse{Payment: toProtoPayment(payment)}, nil
}

func (s *PaymentService) List(ctx context.Context, req *paymentpb.PaymentListRequest) (*paymentpb.PaymentListResponse, error) {
	query := paymentsqlc.New(s.db)
	payments, err := query.FindAllPayments(ctx)
	if err != nil {
		return nil, err
	}
	return &paymentpb.PaymentListResponse{Payments: slice.Convert(payments, toProtoPayment)}, nil
}

func toProtoPayment(payment paymentsqlc.Payment) *paymentpb.Payment {
	return &paymentpb.Payment{
		ExternalRef:  payment.ExternalRef,
		ClientId:     payment.ClientID,
		Amount:       payment.Amount,
		Status:       toProtoPaymentStatus(payment.Status),
		Insufficient: payment.Insufficient,
	}
}

func toProtoPaymentStatus(status paymentsqlc.PaymentStatus) paymentpb.Payment_Status {
	return paymentpb.Payment_Status(paymentpb.Payment_Status_value[string(status)])
}
