package reserve

import (
	"context"
	"database/sql"

	"github.com/google/uuid"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/reserve"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/models"
)

type ReserveService struct {
	reservepb.UnimplementedReserveServiceServer
	db  *sql.DB
	cfg *config.Config
}

func NewReserveService(db *sql.DB, cfg *config.Config) *ReserveService {
	return &ReserveService{
		db:  db,
		cfg: cfg,
	}
}

func (s *ReserveService) Create(ctx context.Context, req *reservepb.ReserveCreateRequest) (*reservepb.ReserveCreateResponse, error) {
	if req.Body == nil {
		return nil, status.Errorf(codes.InvalidArgument, "reserve body is required")
	}

	reserveID := uuid.New().String()

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

	// Insert reserve
	_, err = tx.ExecContext(ctx, `
		INSERT INTO reserves (id, external_ref, status, created_at, updated_at)
		VALUES ($1, $2, $3, NOW(), NOW())`,
		reserveID,
		req.Body.ExternalRef,
		int(models.ReserveStatusCreated),
	)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to create reserve: %v", err)
	}

	// Insert reserve items
	for _, item := range req.Body.Items {
		_, err = tx.ExecContext(ctx, `
			INSERT INTO reserve_items (id, reserve_id, item_id, amount, reserved)
			VALUES ($1, $2, $3, $4, $5)`,
			uuid.New().String(),
			reserveID,
			item.Id,
			item.Amount,
			false,
		)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to create reserve item: %v", err)
		}
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &reservepb.ReserveCreateResponse{
		Id: reserveID,
	}, nil
}

func (s *ReserveService) Approve(ctx context.Context, req *reservepb.ReserveApproveRequest) (*reservepb.ReserveApproveResponse, error) {
	// Get reserve
	reserve, err := s.getReserveByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Check if reserve can be approved
	if reserve.Status != models.ReserveStatusCreated {
		return nil, status.Errorf(codes.FailedPrecondition, "reserve cannot be approved in current status")
	}

	// Get reserve items
	reserveItems, err := s.getReserveItems(ctx, req.Id)
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

	var responseItems []*reservepb.ReserveApproveResponse_Item
	newStatus := models.ReserveStatusApproved

	// Check availability and reserve items
	for _, reserveItem := range reserveItems {
		// Get warehouse item
		warehouseItem, err := s.getWarehouseItem(ctx, reserveItem.ItemID)
		if err != nil {
			return nil, err
		}

		available := warehouseItem.Amount - warehouseItem.Reserved
		canReserve := available >= reserveItem.Amount

		responseItem := &reservepb.ReserveApproveResponse_Item{
			Id:       reserveItem.ItemID,
			Reserved: canReserve,
		}

		if canReserve {
			// Reserve the item in warehouse
			_, err = tx.ExecContext(ctx, `
				UPDATE warehouse_items SET reserved = reserved + $1, updated_at = NOW() 
				WHERE id = $2`, reserveItem.Amount, reserveItem.ItemID)
			if err != nil {
				return nil, status.Errorf(codes.Internal, "failed to reserve item: %v", err)
			}

			// Update reserve item
			_, err = tx.ExecContext(ctx, `
				UPDATE reserve_items SET reserved = true WHERE id = $1`, reserveItem.ID)
			if err != nil {
				return nil, status.Errorf(codes.Internal, "failed to update reserve item: %v", err)
			}
		} else {
			// Mark as insufficient
			insufficientQuantity := reserveItem.Amount - available
			responseItem.InsufficientQuantity = insufficientQuantity
			newStatus = models.ReserveStatusInsufficient

			_, err = tx.ExecContext(ctx, `
				UPDATE reserve_items SET insufficient = $1 WHERE id = $2`,
				insufficientQuantity, reserveItem.ID)
			if err != nil {
				return nil, status.Errorf(codes.Internal, "failed to update reserve item: %v", err)
			}
		}

		responseItems = append(responseItems, responseItem)
	}

	// Update reserve status
	_, err = tx.ExecContext(ctx, `
		UPDATE reserves SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(newStatus), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update reserve status: %v", err)
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &reservepb.ReserveApproveResponse{
		Id:     req.Id,
		Status: reservepb.Reserve_Status(newStatus),
		Items:  responseItems,
	}, nil
}

func (s *ReserveService) Release(ctx context.Context, req *reservepb.ReserveReleaseRequest) (*reservepb.ReserveReleaseResponse, error) {
	// Get reserve
	reserve, err := s.getReserveByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Check if reserve can be released
	if reserve.Status != models.ReserveStatusApproved {
		return nil, status.Errorf(codes.FailedPrecondition, "reserve cannot be released in current status")
	}

	// Get reserve items
	reserveItems, err := s.getReserveItems(ctx, req.Id)
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

	// Release items from warehouse and reduce amounts
	for _, reserveItem := range reserveItems {
		if reserveItem.Reserved {
			// Reduce warehouse item amount and reserved count
			_, err = tx.ExecContext(ctx, `
				UPDATE warehouse_items 
				SET amount = amount - $1, reserved = reserved - $1, updated_at = NOW() 
				WHERE id = $2`, reserveItem.Amount, reserveItem.ItemID)
			if err != nil {
				return nil, status.Errorf(codes.Internal, "failed to release item: %v", err)
			}
		}
	}

	// Update reserve status
	_, err = tx.ExecContext(ctx, `
		UPDATE reserves SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.ReserveStatusReleased), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update reserve status: %v", err)
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &reservepb.ReserveReleaseResponse{
		Id:     req.Id,
		Status: reservepb.Reserve_Status(models.ReserveStatusReleased),
	}, nil
}

func (s *ReserveService) Cancel(ctx context.Context, req *reservepb.ReserveCancelRequest) (*reservepb.ReserveCancelResponse, error) {
	// Get reserve
	_, err := s.getReserveByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Get reserve items
	reserveItems, err := s.getReserveItems(ctx, req.Id)
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

	// Release reserved items back to warehouse
	for _, reserveItem := range reserveItems {
		if reserveItem.Reserved {
			_, err = tx.ExecContext(ctx, `
				UPDATE warehouse_items SET reserved = reserved - $1, updated_at = NOW() 
				WHERE id = $2`, reserveItem.Amount, reserveItem.ItemID)
			if err != nil {
				return nil, status.Errorf(codes.Internal, "failed to release item: %v", err)
			}
		}
	}

	// Update reserve status
	_, err = tx.ExecContext(ctx, `
		UPDATE reserves SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.ReserveStatusCancelled), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update reserve status: %v", err)
	}

	if req.PreparedTransactionId == nil {
		if err = tx.Commit(); err != nil {
			return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
		}
	}

	return &reservepb.ReserveCancelResponse{
		Id:     req.Id,
		Status: reservepb.Reserve_Status(models.ReserveStatusCancelled),
	}, nil
}

func (s *ReserveService) Get(ctx context.Context, req *reservepb.ReserveGetRequest) (*reservepb.ReserveGetResponse, error) {
	reserve, err := s.getReserveByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	reserveProto, err := s.reserveToProto(ctx, reserve)
	if err != nil {
		return nil, err
	}

	return &reservepb.ReserveGetResponse{
		Reserve: reserveProto,
	}, nil
}

func (s *ReserveService) List(ctx context.Context, req *reservepb.ReserveListRequest) (*reservepb.ReserveListResponse, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, external_ref, status, created_at, updated_at
		FROM reserves ORDER BY created_at DESC`)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list reserves: %v", err)
	}
	defer rows.Close()

	var reserves []*reservepb.Reserve
	for rows.Next() {
		reserve := &models.Reserve{}
		err := rows.Scan(
			&reserve.ID, &reserve.ExternalRef, &reserve.Status,
			&reserve.CreatedAt, &reserve.UpdatedAt,
		)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to scan reserve: %v", err)
		}

		reserveProto, err := s.reserveToProto(ctx, reserve)
		if err != nil {
			return nil, err
		}
		reserves = append(reserves, reserveProto)
	}

	return &reservepb.ReserveListResponse{
		Reserves: reserves,
	}, nil
}

func (s *ReserveService) getReserveByID(ctx context.Context, reserveID string) (*models.Reserve, error) {
	reserve := &models.Reserve{}
	err := s.db.QueryRowContext(ctx, `
		SELECT id, external_ref, status, created_at, updated_at
		FROM reserves WHERE id = $1`, reserveID).Scan(
		&reserve.ID, &reserve.ExternalRef, &reserve.Status,
		&reserve.CreatedAt, &reserve.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, status.Errorf(codes.NotFound, "reserve not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to get reserve: %v", err)
	}
	return reserve, nil
}

func (s *ReserveService) getReserveItems(ctx context.Context, reserveID string) ([]*models.ReserveItem, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, reserve_id, item_id, amount, insufficient, reserved
		FROM reserve_items WHERE reserve_id = $1`, reserveID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to get reserve items: %v", err)
	}
	defer rows.Close()

	var items []*models.ReserveItem
	for rows.Next() {
		item := &models.ReserveItem{}
		err := rows.Scan(
			&item.ID, &item.ReserveID, &item.ItemID, &item.Amount,
			&item.Insufficient, &item.Reserved,
		)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to scan reserve item: %v", err)
		}
		items = append(items, item)
	}

	return items, nil
}

func (s *ReserveService) getWarehouseItem(ctx context.Context, itemID string) (*models.WarehouseItem, error) {
	item := &models.WarehouseItem{}
	err := s.db.QueryRowContext(ctx, `
		SELECT id, amount, reserved, unit_cost, updated_at
		FROM warehouse_items WHERE id = $1`, itemID).Scan(
		&item.ID, &item.Amount, &item.Reserved, &item.UnitCost, &item.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, status.Errorf(codes.NotFound, "warehouse item not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to get warehouse item: %v", err)
	}
	return item, nil
}

func (s *ReserveService) reserveToProto(ctx context.Context, reserve *models.Reserve) (*reservepb.Reserve, error) {
	// Get reserve items
	reserveItems, err := s.getReserveItems(ctx, reserve.ID)
	if err != nil {
		return nil, err
	}

	var items []*reservepb.Reserve_Item
	for _, item := range reserveItems {
		protoItem := &reservepb.Reserve_Item{
			Id:       item.ItemID,
			Amount:   item.Amount,
			Reserved: item.Reserved,
		}
		if item.Insufficient != nil {
			protoItem.Insufficient = item.Insufficient
		}
		items = append(items, protoItem)
	}

	return &reservepb.Reserve{
		Id:          reserve.ID,
		ExternalRef: reserve.ExternalRef,
		Status:      reservepb.Reserve_Status(reserve.Status),
		Items:       items,
	}, nil
}
