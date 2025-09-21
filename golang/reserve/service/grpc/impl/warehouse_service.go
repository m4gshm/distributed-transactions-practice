package impl

import (
	"context"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/tx"
	warehousepb "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	whsqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/warehouse/sqlc/gen"
	"github.com/m4gshm/gollections/slice"
)

type WarehouseService struct {
	warehousepb.UnimplementedWarehouseItemServiceServer
	db *pgxpool.Pool
}

func NewWarehouseService(db *pgxpool.Pool) *WarehouseService {
	return &WarehouseService{
		db: db,
	}
}

func (s *WarehouseService) GetItemCost(ctx context.Context, req *warehousepb.GetItemCostRequest) (*warehousepb.GetItemCostResponse, error) {
	query := whsqlc.New(s.db)
	item, err := query.SelectItemByID(ctx, req.Id)
	if err != nil {
		// if err == sql.ErrNoRows {
		// 	return nil, status.Errorf(codes.NotFound, "warehouse item not found")
		// }
		return nil, status.Errorf(grpc.Status(err), "failed to get warehouse item cost (itemID [%s]): %w", req.Id, err)
	}
	return &warehousepb.GetItemCostResponse{Cost: item.UnitCost}, nil
}

func (s *WarehouseService) ItemList(ctx context.Context, req *warehousepb.ItemListRequest) (*warehousepb.ItemListResponse, error) {
	query := whsqlc.New(s.db)
	rows, err := query.SelectAllItems(ctx)
	if err != nil {
		return nil, status.Errorf(grpc.Status(err), "failed to list warehouse items: %w", err)
	}

	items := slice.Convert(rows, func(item whsqlc.WarehouseItem) *warehousepb.Item {
		return &warehousepb.Item{
			Id:        item.ID,
			Amount:    item.Amount,
			Reserved:  item.Reserved,
			UpdatedAt: timestamppb.New(item.UpdatedAt.Time),
		}
	})

	return &warehousepb.ItemListResponse{Accounts: items}, nil
}

func (s *WarehouseService) TopUp(ctx context.Context, req *warehousepb.ItemTopUpRequest) (*warehousepb.ItemTopUpResponse, error) {
	body := req.TopUp
	if body == nil {
		return nil, status.Errorf(codes.InvalidArgument, "top up data is required")
	}

	return tx.New(ctx, s.db, func(tx pgx.Tx) (*warehousepb.ItemTopUpResponse, error) {
		itemID := body.Id

		query := whsqlc.New(s.db)
		item, err := query.SelectItemByID(ctx, itemID)
		if err != nil {
			// if err == sql.ErrNoRows {
			// 	return nil, status.Errorf(codes.NotFound, "warehouse item not found")
			// }
			return nil, status.Errorf(grpc.Status(err), "failed to get warehouse item cost (itemID [%s]): %w", itemID, err)
		}

		newAmount := item.Amount + body.Amount
		if err := query.UpdateAmountAndReserved(ctx, whsqlc.UpdateAmountAndReservedParams{
			ID:     itemID,
			Amount: newAmount,
		}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to update warehouse account (itemID [%s]): %w", itemID, err)
		}

		return &warehousepb.ItemTopUpResponse{Amount: newAmount}, nil
	})
}
