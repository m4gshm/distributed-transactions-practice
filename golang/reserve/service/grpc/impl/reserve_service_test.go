package impl

import (
	"context"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/ovechkin-dm/mockio/v2/matchers"
	. "github.com/ovechkin-dm/mockio/v2/mock"
	"github.com/stretchr/testify/assert"

	"github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	ressqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/reserve/sqlc/gen"
	sqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/reserve/sqlc/gen"
	whsqlc "github.com/m4gshm/distributed-transactions-practice/golang/reserve/storage/warehouse/sqlc/gen"
	"github.com/m4gshm/gollections/convert/ptr"
)

func TestReserveApprove(t *testing.T) {
	reserveID := "1"
	reserveItemID := "2"
	reserveItemAmount := int32(10)

	ctx := context.Background()
	ctrl := NewMockController(t)

	db, tx := mockDBTx(ctrl)

	//mock find FindReserveByID
	findReserveByIDRow := Mock[pgx.Row](ctrl)
	WhenSingle(findReserveByIDRow.Scan(
		Any[*string](),
		Any[**string](),
		Any[*sqlc.ReserveStatus](),
		Any[*pgtype.Timestamptz](),
		Any[*pgtype.Timestamptz](),
	)).ThenAnswer(func(args []any) error {
		*args[0].(*string) = reserveID
		*args[2].(*sqlc.ReserveStatus) = sqlc.ReserveStatusCREATED
		return nil
	})
	WhenSingle(tx.QueryRow(AnyContext(), Exact(ressqlc.FindReserveByID), Equal(reserveID))).ThenReturn(findReserveByIDRow)

	// mock FindItemsByReserveID
	findItemsByReserveIDRows := Mock[pgx.Rows](ctrl)
	WhenSingle(findItemsByReserveIDRows.Next()).ThenAnswer(returnOnce(true))
	WhenSingle(findItemsByReserveIDRows.Scan(
		Any[any](),
		Any[any](),
		Any[any](),
		Any[any](),
		Any[any](),
	)).ThenAnswer(func(args []any) error {
		*args[0].(*string) = reserveItemID
		*args[1].(*string) = reserveID
		*args[2].(*int32) = reserveItemAmount
		return nil
	})
	WhenDouble(tx.Query(AnyContext(), Exact(ressqlc.FindItemsByReserveID), Equal(reserveID))).ThenReturn(findItemsByReserveIDRows, nil)

	// mock SelectItemByIDForUpdate
	selectItemByIDForUpdateRow := Mock[pgx.Row](ctrl)
	WhenSingle(selectItemByIDForUpdateRow.Scan(
		Any[*string](),
		Any[*int32](),
		Any[*int32](),
		Any[*float64](),
		Any[*pgtype.Timestamptz](),
	)).ThenAnswer(func(args []any) error {
		*args[0].(*string) = reserveItemID
		*args[1].(*int32) = reserveItemAmount
		return nil
	})
	WhenSingle(tx.QueryRow(AnyContext(), Exact(whsqlc.SelectItemByIDForUpdate), Equal(reserveItemID))).ThenReturn(selectItemByIDForUpdateRow)

	reserveService := NewReserveService(db, ressqlc.New, whsqlc.New)

	// approve reserve
	r, err := reserveService.Approve(ctx, &gen.ReserveApproveRequest{
		Id: reserveID,
	})

	assert.NoError(t, err)
	assert.NotNil(t, r)
	assert.Equal(t, reserveID, r.Id)
	assert.Equal(t, gen.Reserve_APPROVED, r.Status)

	//check queries
	urip := ressqlc.UpsertReserveItemParams{
		ID:        reserveItemID,
		ReserveID: reserveID,
		Reserved:  ptr.Of(true),
	}
	Verify(tx, Once()).Exec(AnyContext(), Exact(ressqlc.UpsertReserveItem),
		Exact(urip.ID),
		Exact(urip.ReserveID),
		Exact(urip.Amount),
		Equal(urip.Reserved),
		Exact(urip.Insufficient),
	)
	Verify(tx, Once()).Exec(AnyContext(), Exact(whsqlc.IncrementReserved),
		Exact(urip.ID),
		Exact(reserveItemAmount),
	)
	upr := ressqlc.UpsertReserveParams{
		ID:     reserveID,
		Status: ressqlc.ReserveStatusAPPROVED,
	}
	Verify(tx, Once()).Exec(AnyContext(), Exact(ressqlc.UpsertReserve),
		Exact(upr.ID),
		Exact(upr.ExternalRef),
		Exact(upr.Status),
		Any[pgtype.Timestamptz](),
		Any[pgtype.Timestamptz](),
	)
}

func mockDBTx(ctrl *matchers.MockController) (DB, pgx.Tx) {
	db := Mock[DB](ctrl)
	tx := Mock[pgx.Tx](ctrl)
	WhenDouble(db.BeginTx(AnyContext(), Any[pgx.TxOptions]())).ThenReturn(tx, nil)
	return db, tx
}

func returnOnce[T any](val T) func(args []any) T {
	var zero T
	called := false
	return func(args []any) T {
		if called {
			return zero
		}
		called = true
		return val
	}
}

func TestReserveApprove_Incufficient(t *testing.T) {
	reserveID := "1"
	reserveItemID := "2"
	reserveItemAmount := int32(10)

	ctx := context.Background()
	ctrl := NewMockController(t)
	db, _ := mockDBTx(ctrl)

	resq := Mock[ressqlc.Querier](ctrl)
	whq := Mock[whsqlc.Querier](ctrl)

	WhenDouble(resq.FindReserveByID(AnyContext(), Exact(reserveID))).ThenReturn(sqlc.Reserve{
		ID:     reserveID,
		Status: ressqlc.ReserveStatusCREATED,
	}, nil)
	WhenDouble(resq.FindItemsByReserveID(AnyContext(), Exact(reserveID))).ThenReturn([]sqlc.ReserveItem{
		sqlc.ReserveItem{
			ID:        reserveItemID,
			ReserveID: reserveID,
			Amount:    reserveItemAmount,
		},
	}, nil)
	WhenDouble(whq.SelectItemByIDForUpdate(AnyContext(), Exact(reserveItemID))).ThenReturn(whsqlc.WarehouseItem{
		ID:       reserveItemID,
		Amount:   reserveItemAmount,
		Reserved: 2,
	}, nil)
	uripCap := Captor[sqlc.UpsertReserveItemParams]()
	WhenSingle(resq.UpsertReserveItem(AnyContext(), uripCap.Capture())).ThenReturn(nil)
	urpCap := Captor[sqlc.UpsertReserveParams]()
	WhenSingle(resq.UpsertReserve(AnyContext(), urpCap.Capture())).ThenReturn(nil)

	// approve reserve
	reserveService := NewReserveService(db, func(tx sqlc.DBTX) ressqlc.Querier { return resq }, func(tx whsqlc.DBTX) whsqlc.Querier { return whq })
	r, err := reserveService.Approve(ctx, &gen.ReserveApproveRequest{
		Id: reserveID,
	})

	assert.NoError(t, err)
	assert.NotNil(t, r)
	assert.Equal(t, reserveID, r.Id)
	assert.Equal(t, gen.Reserve_INSUFFICIENT, r.Status)

	urips := uripCap.Values()
	assert.Equal(t, 1, len(urips))
	utip := urips[0]
	assert.False(t, *utip.Reserved)
	assert.Equal(t, int32(2), *utip.Insufficient)
	assert.Equal(t, int32(0), utip.Amount)

	urps := urpCap.Values()
	assert.Equal(t, 1, len(urps))
	urp := urps[0]
	assert.Equal(t, reserveID, urp.ID)
	assert.Equal(t, sqlc.ReserveStatusINSUFFICIENT, urp.Status)
}
