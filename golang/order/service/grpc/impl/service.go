package impl

import (
	"context"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/check"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/pg"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/tx"
	orderspb "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/gen"
	sqlc "github.com/m4gshm/distributed-transactions-practice/golang/order/storage/sqlc/gen"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
	"github.com/m4gshm/gollections/convert/ptr"
	"github.com/m4gshm/gollections/op"
	"github.com/m4gshm/gollections/slice"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"
	"time"
)

//go:generate fieldr -type OrderService -out . new-full

// generate.fieldr: new-full
type OrderService struct {
	orderspb.UnimplementedOrderServiceServer
	db        DB
	payment   paymentpb.PaymentServiceClient
	reserve   reservepb.ReserveServiceClient
	warehouse reservepb.WarehouseItemServiceClient
}

type DB interface {
	tx.DB
	sqlc.DBTX
}

func NewOrderService(
	db DB,
	payment paymentpb.PaymentServiceClient,
	reserve reservepb.ReserveServiceClient,
	warehouse reservepb.WarehouseItemServiceClient,
) *OrderService {
	return &OrderService{
		db:        db,
		payment:   payment,
		reserve:   reserve,
		warehouse: warehouse,
	}
}

func (s *OrderService) Create(ctx context.Context, req *orderspb.OrderCreateRequest) (*orderspb.OrderCreateResponse, error) {
	body := req.Body
	if body == nil {
		return nil, status.Errorf(codes.InvalidArgument, "order body is required")
	}
	return tx.New(ctx, s.db, func(tx pgx.Tx) (*orderspb.OrderCreateResponse, error) {
		query := sqlc.New(tx)

		orderID := uuid.New().String()
		customerId := body.CustomerId
		orderEntity := sqlc.InsertOrUpdateOrderParams{
			ID:         orderID,
			CustomerID: customerId,
			Status:     sqlc.OrderStatusCREATING,
			CreatedAt:  pg.Timestamptz(time.Now()),
		}

		if err := query.InsertOrUpdateOrder(ctx, orderEntity); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to create order '%s': %v", orderID, err)
		}

		if delivery := body.Delivery; delivery != nil {
			if err := query.InsertOrUpdateDelivery(ctx, sqlc.InsertOrUpdateDeliveryParams{
				OrderID: orderID,
				Address: delivery.Address,
				Type:    sqlc.DeliveryType(delivery.Type.String()),
			}); err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to create delivery (order '%s', address '%s'): %v", orderID, delivery.Address, err)
			}
		}

		items := []sqlc.Item{}
		for _, item := range body.Items {
			ins := sqlc.InsertOrUpdateItemParams{
				OrderID: orderID,
				ID:      item.Id,
				Amount:  item.Amount,
			}
			if err := query.InsertOrUpdateItem(ctx, ins); err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to create item (order '%s', item '%s'): %v", ins.OrderID, ins.ID, err)
			}
			items = append(items, sqlc.Item{
				ID:      item.Id,
				OrderID: orderID,
				Amount:  item.Amount,
			})
		}
		if _, err := s.create(ctx, query, orderID, customerId, items); err != nil {
			return nil, err
		}
		return &orderspb.OrderCreateResponse{Id: orderID}, nil
	})
}

func (s *OrderService) create(
	ctx context.Context, query *sqlc.Queries, orderID string, customerID string, items []sqlc.Item,
) (sqlc.OrderStatus, error) {
	cost := 0.0
	reserveItems := []*reservepb.ReserveCreateRequest_Reserve_Item{}
	for _, item := range items {
		id := item.ID
		itemCost, err := s.warehouse.GetItemCost(ctx, &reservepb.GetItemCostRequest{Id: id})
		if err != nil {
			return "", status.Errorf(grpc.Status(err), "failed to get item cost (order '%s', item '%s'): %v", orderID, id, err)
		}
		cost += float64(item.Amount) * itemCost.GetCost()
		reserveItems = append(reserveItems, &reservepb.ReserveCreateRequest_Reserve_Item{
			Id:     id,
			Amount: item.Amount,
		})
	}

	paymentID := ""
	if resp, err := s.payment.Create(ctx, &paymentpb.PaymentCreateRequest{Body: &paymentpb.PaymentCreateRequest_PaymentCreate{
		ExternalRef: orderID,
		ClientId:    customerID,
		Amount:      cost,
	}}); err != nil {
		return "", status.Errorf(grpc.Status(err), "failed to create payment (order '%s'): %v", orderID, err)
	} else {
		paymentID = resp.Id
	}

	reserveID := ""
	if resp, err := s.reserve.Create(ctx, &reservepb.ReserveCreateRequest{Body: &reservepb.ReserveCreateRequest_Reserve{
		ExternalRef: orderID,
		Items:       reserveItems,
	}}); err != nil {
		return "", status.Errorf(grpc.Status(err), "failed to create reserve (order '%s'): %v", orderID, err)
	} else {
		reserveID = resp.Id
	}

	finalStatus := sqlc.OrderStatusCREATED
	if err := query.InsertOrUpdateOrder(ctx, sqlc.InsertOrUpdateOrderParams{
		ID:        orderID,
		Status:    finalStatus,
		CreatedAt: pg.Timestamptz(time.Now()),
		PaymentID: pg.String(paymentID),
		ReserveID: pg.String(reserveID),
	}); err != nil {
		return "", status.Errorf(grpc.Status(err), "failed to update order '%s' by status '%s': %v", orderID, finalStatus, err)
	}
	return finalStatus, nil
}

func (s *OrderService) Approve(ctx context.Context, req *orderspb.OrderApproveRequest) (*orderspb.OrderApproveResponse, error) {
	query := sqlc.New(s.db)
	finalStatus, err := s.appvove(ctx, query, req.Id, req.TwoPhaseCommit)
	if err != nil {
		return nil, err
	}
	return &orderspb.OrderApproveResponse{Id: req.Id, Status: toProtoOrderStatus(finalStatus)}, nil
}

func (s *OrderService) appvove(ctx context.Context, query *sqlc.Queries, orderID string, twoPhaseCommit bool) (sqlc.OrderStatus, error) {
	intermediateStatus := sqlc.OrderStatusAPPROVING
	finalStatus := sqlc.OrderStatusAPPROVED
	expecteds := slice.Of(sqlc.OrderStatusCREATED, sqlc.OrderStatusINSUFFICIENT, intermediateStatus)
	row, err := query.FindOrderById(ctx, orderID)
	if err != nil {
		return "", err
	}
	if err := s.doWorkflow(ctx, query, row.Order, expecteds, intermediateStatus, finalStatus, twoPhaseCommit, s.approve_); err != nil {
		return "", err
	}
	return finalStatus, nil
}

func (s *OrderService) Release(ctx context.Context, req *orderspb.OrderReleaseRequest) (*orderspb.OrderReleaseResponse, error) {
	query := sqlc.New(s.db)
	finalStatus, err := s.release(ctx, query, req.Id, req.TwoPhaseCommit)
	if err != nil {
		return nil, err
	}
	return &orderspb.OrderReleaseResponse{Id: req.Id, Status: toProtoOrderStatus(finalStatus)}, nil
}

func (s *OrderService) release(ctx context.Context, query *sqlc.Queries, orderID string, twoPhaseCommit bool) (sqlc.OrderStatus, error) {
	intermediateStatus := sqlc.OrderStatusRELEASING
	finalStatus := sqlc.OrderStatusRELEASED
	expecteds := slice.Of(sqlc.OrderStatusAPPROVED, intermediateStatus)
	if row, err := query.FindOrderById(ctx, orderID); err != nil {
		return "", err
	} else if err := s.doWorkflow(ctx, query, row.Order, expecteds, intermediateStatus, finalStatus, twoPhaseCommit, s.release_); err != nil {
		return "", err
	}
	return finalStatus, nil
}

func (s *OrderService) Cancel(ctx context.Context, req *orderspb.OrderCancelRequest) (*orderspb.OrderCancelResponse, error) {
	query := sqlc.New(s.db)
	_, err := s.cancel(ctx, query, req.Id, req.TwoPhaseCommit)
	if err != nil {
		return nil, err
	}
	return &orderspb.OrderCancelResponse{Id: req.Id}, nil
}

func (s *OrderService) cancel(ctx context.Context, query *sqlc.Queries, orderID string, twoPhaseCommit bool) (sqlc.OrderStatus, error) {
	intermediateStatus := sqlc.OrderStatusCANCELLING
	finalStatus := sqlc.OrderStatusCANCELLED
	expecteds := slice.Of(sqlc.OrderStatusCREATED, sqlc.OrderStatusINSUFFICIENT, sqlc.OrderStatusAPPROVED, intermediateStatus)
	if row, err := query.FindOrderById(ctx, orderID); err != nil {
		return "", err
	} else if err := s.doWorkflow(ctx, query, row.Order, expecteds, intermediateStatus, finalStatus, twoPhaseCommit, s.cancel_); err != nil {
		return "", err
	}
	return finalStatus, nil
}

func (s *OrderService) Resume(ctx context.Context, req *orderspb.OrderResumeRequest) (*orderspb.OrderResumeResponse, error) {
	query := sqlc.New(s.db)

	orderID := req.Id
	row, err := query.FindOrderById(ctx, orderID)
	if err != nil {
		return nil, err
	}
	order := row.Order
	items, err := query.FindItemsByOrderId(ctx, orderID)
	if err != nil {
		return nil, err
	}

	var orderStatus sqlc.OrderStatus
	// var err error
	switch order.Status {
	case sqlc.OrderStatusCREATED, sqlc.OrderStatusAPPROVED, sqlc.OrderStatusRELEASED, sqlc.OrderStatusCANCELLED:
		err = status.Error(codes.FailedPrecondition, "already committed status")
	case sqlc.OrderStatusCREATING:
		orderStatus, err = s.create(ctx, query, orderID, order.CustomerID, items)
	case sqlc.OrderStatusINSUFFICIENT, sqlc.OrderStatusAPPROVING:
		orderStatus, err = s.appvove(ctx, query, orderID, req.TwoPhaseCommit)
	case sqlc.OrderStatusRELEASING:
		orderStatus, err = s.release(ctx, query, orderID, req.TwoPhaseCommit)
	case sqlc.OrderStatusCANCELLING:
		orderStatus, err = s.cancel(ctx, query, orderID, req.TwoPhaseCommit)
	}

	if err != nil {
		return nil, err
	}
	return &orderspb.OrderResumeResponse{Id: orderID, Status: *toProtoOrderStatus(orderStatus)}, nil
}

func (s *OrderService) Get(ctx context.Context, req *orderspb.OrderGetRequest) (*orderspb.OrderGetResponse, error) {
	query := sqlc.New(s.db)

	r, err := query.FindOrderById(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	orderProto := toProtoOrder(r.Order, r.Delivery)

	return &orderspb.OrderGetResponse{Order: orderProto}, nil
}

func (s *OrderService) List(ctx context.Context, req *orderspb.OrderListRequest) (*orderspb.OrderListResponse, error) {
	query := sqlc.New(s.db)

	rows, err := query.FindAllOrders(ctx)
	if err != nil {
		return nil, err
	}

	return &orderspb.OrderListResponse{Orders: slice.Convert(rows, func(row sqlc.FindAllOrdersRow) *orderspb.Order {
		return toProtoOrder(row.Order, row.Delivery)
	})}, nil
}

func (s *OrderService) doWorkflow(ctx context.Context, query *sqlc.Queries, order sqlc.Order, expecteds []sqlc.OrderStatus, intermediateStatus sqlc.OrderStatus,
	finalStatus sqlc.OrderStatus, twoPhaseCommit bool, routine func(context.Context, sqlc.Order, bool) error,
) error {
	if err := check.Status("order", order.Status, expecteds...); err != nil {
		return err
	}

	if order.Status != intermediateStatus {
		if err := updateOrderStatus(ctx, query, intermediateStatus, order.ID); err != nil {
			return err
		}
	}

	if err := routine(ctx, order, twoPhaseCommit); err != nil {
		return err
	}

	if err := updateOrderStatus(ctx, query, finalStatus, order.ID); err != nil {
		return err
	}
	return nil
}

func (s *OrderService) approve_(ctx context.Context, order sqlc.Order, twoPhaseCommit bool) error {
	paymentId := order.PaymentID.String
	reserveId := order.ReserveID.String

	if _, err := s.payment.Approve(ctx, &paymentpb.PaymentApproveRequest{
		Id:                    paymentId,
		PreparedTransactionId: paymentTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "payment approving failed (paymentId '%s'): %v", paymentId, err)
	}

	if _, err := s.reserve.Approve(ctx, &reservepb.ReserveApproveRequest{
		Id:                    reserveId,
		PreparedTransactionId: reserveTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "reserve approving failed (reserveId '%s'): %v", reserveId, err)
	}

	return nil
}

func (s *OrderService) cancel_(ctx context.Context, order sqlc.Order, twoPhaseCommit bool) error {
	paymentId := order.PaymentID.String
	reserveId := order.ReserveID.String

	if _, err := s.payment.Cancel(ctx, &paymentpb.PaymentCancelRequest{
		Id:                    paymentId,
		PreparedTransactionId: paymentTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "payment cancelling failed (paymentId '%s'): %v", paymentId, err)
	}

	if _, err := s.reserve.Cancel(ctx, &reservepb.ReserveCancelRequest{
		Id:                    reserveId,
		PreparedTransactionId: reserveTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "reserve cancelling failed (reserveId '%s'): %v", reserveId, err)
	}

	return nil
}

func (s *OrderService) release_(ctx context.Context, order sqlc.Order, twoPhaseCommit bool) error {
	paymentId := order.PaymentID.String
	reserveId := order.ReserveID.String

	if _, err := s.payment.Pay(ctx, &paymentpb.PaymentPayRequest{
		Id:                    paymentId,
		PreparedTransactionId: paymentTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "payment pay failed (paymentId '%s'): %v", paymentId, err)
	}

	if _, err := s.reserve.Release(ctx, &reservepb.ReserveReleaseRequest{
		Id:                    reserveId,
		PreparedTransactionId: reserveTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "reserve release failed (reserveId '%s'): %v", reserveId, err)
	}

	return nil
}

func toProtDelivery(delivery sqlc.Delivery) *orderspb.Order_Delivery {
	return &orderspb.Order_Delivery{
		Address: delivery.Address,
		Type:    toProtoDeliveryType(delivery),
	}
}

func toProtoDeliveryType(delivery sqlc.Delivery) orderspb.Order_Delivery_Type {
	return orderspb.Order_Delivery_Type(orderspb.Order_Delivery_Type_value[string(delivery.Type)])
}

func toProtoOrder(order sqlc.Order, delivery sqlc.Delivery) *orderspb.Order {
	return &orderspb.Order{
		Id:         order.ID,
		CreatedAt:  timestamppb.New(order.CreatedAt.Time),
		UpdatedAt:  timestamppb.New(order.UpdatedAt.Time),
		CustomerId: order.CustomerID,
		Delivery:   toProtDelivery(delivery),
		Status:     *toProtoOrderStatus(order.Status),
	}
}

func toProtoOrderStatus(status sqlc.OrderStatus) *orderspb.Order_Status {
	return ptr.Of(orderspb.Order_Status(orderspb.Order_Status_value[string(status)]))
}

func reserveTransactionID(support bool, order sqlc.Order) *string {
	return op.IfElse(support, &order.ReserveTransactionID.String, nil)
}

func paymentTransactionID(support bool, order sqlc.Order) *string {
	return op.IfElse(support, &order.PaymentTransactionID.String, nil)
}

func updateOrderStatus(ctx context.Context, query *sqlc.Queries, finalStatus sqlc.OrderStatus, orderId string) error {
	err := query.UpdateOrderStatus(ctx, sqlc.UpdateOrderStatusParams{Status: finalStatus})
	if err != nil {
		return status.Errorf(grpc.Status(err), "failed to update order status (orderId '%s', status '%s'): %v",
			orderId, finalStatus, err)
	}
	return nil
}
