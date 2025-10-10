package impl

import (
	"context"
	"testing"

	. "github.com/ovechkin-dm/mockio/v2/mock"

	"github.com/jackc/pgx/v5"
	"github.com/stretchr/testify/assert"

	orderspb "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/gen"
	"github.com/m4gshm/distributed-transactions-practice/golang/order/storage/sqlc/gen"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	"github.com/m4gshm/gollections/collection/immutable"
)

func TestOrderCreate(t *testing.T) {
	ctx := context.Background()
	ctrl := NewMockController(t)

	db := Mock[DB](ctrl)
	tx := Mock[pgx.Tx](ctrl)

	WhenDouble(db.BeginTx(AnyContext(), Any[pgx.TxOptions]())).ThenReturn(tx, nil)

	payment := Mock[paymentpb.PaymentServiceClient](ctrl)
	WhenDouble(payment.Create(AnyContext(), Any[*paymentpb.PaymentCreateRequest]())).ThenReturn(&paymentpb.PaymentCreateResponse{
		Id: "PaymentCreateResponse_1",
	}, nil)

	reserve := Mock[reservepb.ReserveServiceClient](ctrl)
	WhenDouble(reserve.Create(AnyContext(), Any[*reservepb.ReserveCreateRequest]())).ThenReturn(&reservepb.ReserveCreateResponse{
		Id: "PReserveCreateResponse_1",
	}, nil)

	warehouse := Mock[reservepb.WarehouseItemServiceClient](ctrl)
	itemID := "2"
	costReqMatch := CreateMatcher("GetItemCostRequest", MatchCondition(func(actual *reservepb.GetItemCostRequest) bool {
		return actual.Id == itemID
	}))
	WhenDouble(warehouse.GetItemCost(AnyContext(), costReqMatch())).ThenReturn(&reservepb.GetItemCostResponse{Cost: 5}, nil)

	orderService := NewOrderService(db, payment, reserve, warehouse)

	reqBody := &orderspb.OrderCreateRequest_OrderCreate{
		CustomerId: "1",
		Delivery: &orderspb.Order_Delivery{
			Address: "street 1",
			Type:    orderspb.Order_Delivery_COURIER,
		},
		Items: []*orderspb.OrderCreateRequest_OrderCreate_Item{
			{
				Id:     itemID,
				Amount: 100,
			},
		},
	}
	resp, err := orderService.Create(ctx, &orderspb.OrderCreateRequest{
		Body: reqBody,
	})
	assert.NoError(t, err)
	assert.NotNil(t, resp)

	sqlCap := Captor[string]()
	orderIdCap := Captor[string]()
	//upsert Order
	// 	arg.ID,
	// arg.Status,
	// arg.CreatedAt,
	// arg.UpdatedAt,
	// arg.CustomerID,
	// arg.ReserveID,
	// arg.PaymentID,
	// arg.PaymentTransactionID,
	// arg.ReserveTransactionID,

	//create
	Verify(tx, Times(1)).Exec(AnyContext(),
		/*sql*/ sqlCap.Capture(),
		/*ID*/ orderIdCap.Capture(),
		/*status*/ Equal(gen.OrderStatusCREATING),
		Any[any](), Any[any](), Any[any](), Any[any](), Any[any](), Any[any](), Any[any](),
	)

	//update
	Verify(tx, Times(1)).Exec(AnyContext(),
		/*sql*/ sqlCap.Capture(),
		/*ID*/ orderIdCap.Capture(),
		/*status*/ Equal(gen.OrderStatusCREATED),
		Any[any](), Any[any](), Any[any](), Any[any](), Any[any](), Any[any](), Any[any](),
	)

	upsertSqls := immutable.NewSet(sqlCap.Values()...)
	assert.Equal(t, 1, upsertSqls.Len())
	upsertSql, _ := upsertSqls.Head()
	assert.Equal(t, `-- name: InsertOrUpdateOrder :exec
INSERT INTO
  orders (
    id,
    status,
    created_at,
    updated_at,
    customer_id,
    reserve_id,
    payment_id,
    payment_transaction_id,
    reserve_transaction_id
  )
VALUES
  ($1, $2, $3, $4, $5, $6, $7, $8, $9) ON CONFLICT(id) DO
UPDATE
SET
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at,
  reserve_id = COALESCE(EXCLUDED.reserve_id, orders.reserve_id),
  payment_id = COALESCE(EXCLUDED.payment_id, orders.payment_id)
`, upsertSql)

	rcap := Captor[*reservepb.ReserveCreateRequest]()
	Verify(reserve, Once()).Create(AnyContext(), rcap.Capture())

	reserveReq := rcap.Last()
	assert.Equal(t, len(reqBody.Items), len(reserveReq.Body.Items))
	assert.Equal(t, itemID, reserveReq.Body.Items[0].Id)
	assert.Equal(t, int32(100), reserveReq.Body.Items[0].Amount)

	prcap := Captor[*paymentpb.PaymentCreateRequest]()
	Verify(payment, Once()).Create(AnyContext(), prcap.Capture())
	paymentReq := prcap.Last()
	assert.Equal(t, float64(100*5), paymentReq.Body.Amount)

}

func MatchCondition[T any](cond func(actual T) bool) func(allArgs []any, actual T) bool {
	return func(allArgs []any, actual T) bool { return cond(actual) }
}
