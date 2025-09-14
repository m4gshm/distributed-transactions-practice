package reserve

import (
	"context"
	"database/sql"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	warehousepb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/reserve"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/models"
)

type WarehouseService struct {
	warehousepb.UnimplementedWarehouseItemServiceServer
	db  *sql.DB
	cfg *config.Config
}

func NewWarehouseService(db *sql.DB, cfg *config.Config) *WarehouseService {
	return &WarehouseService{
		db:  db,
		cfg: cfg,
	}
}

func (s *WarehouseService) GetItemCost(ctx context.Context, req *warehousepb.GetItemCostRequest) (*warehousepb.GetItemCostResponse, error) {
	var cost float64
	err := s.db.QueryRowContext(ctx, `
		SELECT unit_cost FROM warehouse_items WHERE id = $1`, req.Id).Scan(&cost)

	if err != nil {
		if err == sql.ErrNoRows {
			return nil, status.Errorf(codes.NotFound, "warehouse item not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to get item cost: %v", err)
	}

	return &warehousepb.GetItemCostResponse{
		Cost: cost,
	}, nil
}

func (s *WarehouseService) ItemList(ctx context.Context, req *warehousepb.ItemListRequest) (*warehousepb.ItemListResponse, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, amount, reserved, updated_at
		FROM warehouse_items ORDER BY id`)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list warehouse items: %v", err)
	}
	defer rows.Close()

	var items []*warehousepb.Item
	for rows.Next() {
		item := &models.WarehouseItem{}
		err := rows.Scan(&item.ID, &item.Amount, &item.Reserved, &item.UpdatedAt)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to scan warehouse item: %v", err)
		}

		protoItem := &warehousepb.Item{
			Id:        item.ID,
			Amount:    item.Amount,
			Reserved:  item.Reserved,
			UpdatedAt: timestamppb.New(item.UpdatedAt),
		}
		items = append(items, protoItem)
	}

	return &warehousepb.ItemListResponse{
		Accounts: items, // Note: proto field name is 'accounts' but contains items
	}, nil
}

func (s *WarehouseService) TopUp(ctx context.Context, req *warehousepb.ItemTopUpRequest) (*warehousepb.ItemTopUpResponse, error) {
	if req.TopUp == nil {
		return nil, status.Errorf(codes.InvalidArgument, "top up data is required")
	}

	itemID := req.TopUp.Id
	amount := req.TopUp.Amount

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Check if item exists, create if not
	var currentAmount int32
	err = tx.QueryRowContext(ctx, `
		SELECT amount FROM warehouse_items WHERE id = $1`, itemID).Scan(&currentAmount)

	if err != nil {
		if err == sql.ErrNoRows {
			// Create new warehouse item with default cost
			currentAmount = 0
			_, err = tx.ExecContext(ctx, `
				INSERT INTO warehouse_items (id, amount, reserved, unit_cost, updated_at)
				VALUES ($1, $2, 0, 10.0, NOW())`, itemID, amount)
			if err != nil {
				return nil, status.Errorf(codes.Internal, "failed to create warehouse item: %v", err)
			}
		} else {
			return nil, status.Errorf(codes.Internal, "failed to get warehouse item: %v", err)
		}
	} else {
		// Update existing item
		_, err = tx.ExecContext(ctx, `
			UPDATE warehouse_items SET amount = amount + $1, updated_at = NOW() 
			WHERE id = $2`, amount, itemID)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to update warehouse item: %v", err)
		}
	}

	newAmount := currentAmount + amount

	if err = tx.Commit(); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
	}

	return &warehousepb.ItemTopUpResponse{
		Amount: newAmount,
	}, nil
}
