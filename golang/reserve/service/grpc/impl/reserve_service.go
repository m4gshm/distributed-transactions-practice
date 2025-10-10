package impl

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/m4gshm/gollections/convert"
	"github.com/m4gshm/gollections/convert/val"
	"github.com/m4gshm/gollections/op"
	"github.com/m4gshm/gollections/seq"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/check"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/pg"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/tx"
	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	ressqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/reserve/sqlc/gen"
	whsqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/warehouse/sqlc/gen"
)

//go:generate fieldr -type ReserveService -out . new-full
//go:generate mockgen -destination mock_test.go -package impl . DB
//go:generate mockgen -destination mock_pgx_test.go -package impl github.com/jackc/pgx/v5 Tx,Row

type ReserveService[RQ ressqlc.Querier, WQ whsqlc.Querier] struct {
	reservepb.UnimplementedReserveServiceServer
	db   DB
	resq func(tx ressqlc.DBTX) RQ
	whq  func(tx whsqlc.DBTX) WQ
}

type DB interface {
	tx.DB
	ressqlc.DBTX
}

func NewReserveService[RQ ressqlc.Querier, WQ whsqlc.Querier](
	db DB,
	resq func(tx ressqlc.DBTX) RQ,
	whq func(tx whsqlc.DBTX) WQ,
) *ReserveService[RQ, WQ] {
	return &ReserveService[RQ, WQ]{
		db:   db,
		resq: resq,
		whq:  whq,
	}
}

func (s *ReserveService[RQ, WQ]) Create(ctx context.Context, req *reservepb.ReserveCreateRequest) (*reservepb.ReserveCreateResponse, error) {
	body := req.Body
	if body == nil {
		return nil, status.Errorf(codes.InvalidArgument, "reserve body is required")
	}
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*reservepb.ReserveCreateResponse, error) {
		query := s.resq(tx)
		reserveID := uuid.New().String()

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %w", err)
		// 	}
		// }

		if err := query.UpsertReserve(ctx, ressqlc.UpsertReserveParams{
			ID:          reserveID,
			ExternalRef: &body.ExternalRef,
			CreatedAt:   pg.Timestamptz(time.Now()),
			Status:      ressqlc.ReserveStatusCREATED,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to create reserve (externalRef '%s'): %v", body.ExternalRef, err)
		}

		for _, item := range body.Items {
			if err := query.UpsertReserveItem(ctx, ressqlc.UpsertReserveItemParams{
				ReserveID: reserveID,
				ID:        item.Id,
				Amount:    item.Amount,
			}); err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to create reserve item (externalRef '%s', itemId '%s'): %v", body.ExternalRef, item.Id, err)
			}
		}

		// if req.PreparedTransactionId == nil {
		// 	if err = tx.Commit(); err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %w", err)
		// 	}
		// }

		return &reservepb.ReserveCreateResponse{Id: reserveID}, nil
	})

}

func (s *ReserveService[RQ, WQ]) Approve(ctx context.Context, req *reservepb.ReserveApproveRequest) (*reservepb.ReserveApproveResponse, error) {
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*reservepb.ReserveApproveResponse, error) {
		resQuery := s.resq(tx)
		whQuery := s.whq(tx)

		// Get reserve
		reserve, err := resQuery.FindReserveByID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		if err := check.Status("reserve", reserve.Status, ressqlc.ReserveStatusCREATED); err != nil {
			return nil, err
		}

		reserveItems, err := resQuery.FindItemsByReserveID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %w", err)
		// 	}
		// }

		newStatus := ressqlc.ReserveStatusAPPROVED

		// Check availability and reserve items
		onUpdateItems := []ressqlc.ReserveItem{}
		insufficients := map[string]int32{}
		reservingAmounts := map[string]int32{}
		for _, item := range reserveItems {
			if reserved := item.Reserved; reserved != nil && *reserved {
				continue
			}

			itemID := item.ID

			warehouseItem, err := whQuery.SelectItemByIDForUpdate(ctx, itemID)
			if err != nil {
				//todo
				return nil, err
			}

			available := warehouseItem.Amount - warehouseItem.Reserved
			canReserve := available >= item.Amount
			if canReserve {
				reservingAmounts[itemID] = item.Amount
			} else {
				// Mark as insufficient
				insufficientQuantity := item.Amount - available
				insufficients[itemID] = insufficientQuantity
			}
			onUpdateItems = append(onUpdateItems, item)
		}

		if len(insufficients) > 0 {
			newStatus = ressqlc.ReserveStatusINSUFFICIENT
			reservingAmounts = nil
		}

		reserved := newStatus != ressqlc.ReserveStatusINSUFFICIENT

		for _, item := range onUpdateItems {
			insufficientAmount, ok := insufficients[item.ID]

			item.Reserved = &reserved
			item.Insufficient = op.IfElse(ok, &insufficientAmount, nil)

			if err := resQuery.UpsertReserveItem(ctx, ressqlc.UpsertReserveItemParams{
				ID:           item.ID,
				ReserveID:    item.ReserveID,
				Reserved:     item.Reserved,
				Insufficient: item.Insufficient,
			}); err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to update reserve item (itemId '%s'): %v", item.ID, err)
			}
		}

		for itemId, reserved := range reservingAmounts {
			if err := whQuery.IncrementReserved(ctx, whsqlc.IncrementReservedParams{
				ID:       itemId,
				Reserved: reserved,
			}); err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to increment reserve of warehouse item (itemId '%s'): %v", itemId, err)
			}
		}

		if err := updateReserveStatus(ctx, resQuery, req.Id, newStatus); err != nil {
			return nil, err
		}

		// if req.PreparedTransactionId == nil {
		// 	if err = tx.Commit(); err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %w", err)
		// 	}
		// }

		return &reservepb.ReserveApproveResponse{
			Id:     req.Id,
			Status: toProtoReserveStatus(newStatus),
			Items:  slice.Convert(onUpdateItems, toProtoReserveApproveResponse),
		}, nil
	})
}

func (s *ReserveService[RQ, WQ]) Release(ctx context.Context, req *reservepb.ReserveReleaseRequest) (*reservepb.ReserveReleaseResponse, error) {
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*reservepb.ReserveReleaseResponse, error) {
		resQuery := s.resq(tx)
		whQuery := s.whq(tx)

		reserve, err := resQuery.FindReserveByID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		if err := check.Status("reserve", reserve.Status, ressqlc.ReserveStatusAPPROVED); err != nil {
			return nil, err
		}

		reserveItems, err := resQuery.FindItemsByReserveID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %w", err)
		// 	}
		// }

		if err := releaseWarehouseItems(ctx, whQuery, reserveItems); err != nil {
			return nil, err
		}

		newStatus := ressqlc.ReserveStatusRELEASED
		if err := updateReserveStatus(ctx, resQuery, req.Id, newStatus); err != nil {
			return nil, err
		}

		// if req.PreparedTransactionId == nil {
		// 	if err = tx.Commit(); err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %w", err)
		// 	}
		// }

		return &reservepb.ReserveReleaseResponse{
			Id:     req.Id,
			Status: toProtoReserveStatus(newStatus),
		}, nil
	})
}

func (s *ReserveService[RQ, WQ]) Cancel(ctx context.Context, req *reservepb.ReserveCancelRequest) (*reservepb.ReserveCancelResponse, error) {
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*reservepb.ReserveCancelResponse, error) {
		resQuery := s.resq(tx)
		whQuery := s.whq(tx)

		reserve, err := resQuery.FindReserveByID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		if err := check.Status("reserve", reserve.Status, ressqlc.ReserveStatusAPPROVED); err != nil {
			return nil, err
		}

		reserveItems, err := resQuery.FindItemsByReserveID(ctx, req.Id)
		if err != nil {
			return nil, err
		}

		// // Check if prepared transaction ID is provided for 2PC
		// if req.PreparedTransactionId != nil {
		// 	_, err = tx.ExecContext(ctx, "PREPARE TRANSACTION $1", *req.PreparedTransactionId)
		// 	if err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to prepare transaction: %w", err)
		// 	}
		// }

		// Release reserved items back to warehouse
		if err := unreserveWarehouseItems(ctx, whQuery, reserveItems); err != nil {
			return nil, err
		}

		const newStatus = ressqlc.ReserveStatusCANCELLED
		if err := updateReserveStatus(ctx, resQuery, req.Id, newStatus); err != nil {
			return nil, err
		}

		// if req.PreparedTransactionId == nil {
		// 	if err = tx.Commit(); err != nil {
		// 		return nil, status.Errorf(grpc.Status(err), "failed to commit transaction: %w", err)
		// 	}
		// }

		return &reservepb.ReserveCancelResponse{
			Id:     req.Id,
			Status: toProtoReserveStatus(newStatus),
		}, nil
	})
}

func (s *ReserveService[RQ, WQ]) Get(ctx context.Context, req *reservepb.ReserveGetRequest) (*reservepb.ReserveGetResponse, error) {
	resQuery := s.resq(s.db)

	reserve, err := resQuery.FindReserveByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	reserveItems, err := resQuery.FindItemsByReserveID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	return &reservepb.ReserveGetResponse{Reserve: toProtoReserve(reserve, reserveItems)}, nil
}

func (s *ReserveService[RQ, WQ]) List(ctx context.Context, req *reservepb.ReserveListRequest) (*reservepb.ReserveListResponse, error) {
	resQuery := s.resq(s.db)

	reserves, err := resQuery.FindAllReserves(ctx)
	if err != nil {
		return nil, err
	}

	return &reservepb.ReserveListResponse{
		Reserves: slice.Convert(reserves, func(r ressqlc.Reserve) *reservepb.Reserve {
			return toProtoReserve(r, nil)
		}),
	}, nil
}

func toProtoReserve(reserve ressqlc.Reserve, reserveItems []ressqlc.ReserveItem) *reservepb.Reserve {
	return &reservepb.Reserve{
		Id:          reserve.ID,
		ExternalRef: convert.ToVal(reserve.ExternalRef),
		Status:      toProtoReserveStatus(reserve.Status),
		Items:       slice.Convert(reserveItems, toProtoReserveItem),
	}
}

func toProtoReserveItem(item ressqlc.ReserveItem) *reservepb.Reserve_Item {
	return &reservepb.Reserve_Item{
		Id:           item.ID,
		Amount:       item.Amount,
		Reserved:     convert.ToVal(item.Reserved),
		Insufficient: item.Insufficient,
	}
}

func releaseWarehouseItems[WQ whsqlc.Querier](ctx context.Context, whQuery WQ, items []ressqlc.ReserveItem) error {
	for item := range reservedOnly(items) {
		if err := whQuery.DecrementAmountAndReserved(ctx, whsqlc.DecrementAmountAndReservedParams{
			ID:     item.ID,
			Amount: item.Amount,
		}); err != nil {
			return status.Errorf(grpc.Status(err), "failed to release item (itemID '%s'): %v", item.ID, err)
		}
	}
	return nil
}

func unreserveWarehouseItems[WQ whsqlc.Querier](ctx context.Context, whQuery WQ, items []ressqlc.ReserveItem) error {
	for item := range reservedOnly(items) {
		if err := whQuery.DecrementReserved(ctx, whsqlc.DecrementReservedParams{
			ID:       item.ID,
			Reserved: item.Amount,
		}); err != nil {
			return status.Errorf(grpc.Status(err), "failed to unreserve item (itemID '%s'): %v", item.ID, err)
		}
	}
	return nil
}

func reservedOnly(reserveItems []ressqlc.ReserveItem) seq.Seq[ressqlc.ReserveItem] {
	return seq.Of(reserveItems...).Filter(func(item ressqlc.ReserveItem) bool { return val.Of(item.Reserved) })
}

func updateReserveStatus[RQ ressqlc.Querier](ctx context.Context, resQuery RQ, reserveID string, newStatus ressqlc.ReserveStatus) error {
	timestamp := pg.Timestamptz(time.Now())
	if err := resQuery.UpsertReserve(ctx, ressqlc.UpsertReserveParams{
		ID:        reserveID,
		Status:    newStatus,
		CreatedAt: timestamp,
		UpdatedAt: timestamp,
	}); err != nil {
		return status.Errorf(grpc.Status(err), "failed to update reserve status (reserveId '%s'): %v", reserveID, err)
	}
	return nil
}

func toProtoReserveStatus(newStatus ressqlc.ReserveStatus) reservepb.Reserve_Status {
	return reservepb.Reserve_Status(reservepb.Reserve_Status_value[string(newStatus)])
}

func toProtoReserveApproveResponse(item ressqlc.ReserveItem) *reservepb.ReserveApproveResponse_Item {
	insufficientQuantity := int32(0)
	if i := item.Insufficient; i != nil {
		insufficientQuantity = *i
	}
	reserved := false
	if item.Reserved != nil {
		reserved = *item.Reserved
	}
	return &reservepb.ReserveApproveResponse_Item{
		Id:                   item.ID,
		InsufficientQuantity: insufficientQuantity,
		Reserved:             reserved,
	}
}
