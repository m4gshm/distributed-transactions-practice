package impl

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/m4gshm/gollections/convert/ptr"
	"github.com/m4gshm/gollections/op"
	"github.com/m4gshm/gollections/slice"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/check"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/grpc"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/pg"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/tx"
	orderspb "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/gen"
	sqlc "github.com/m4gshm/distributed-transactions-practice/golang/order/storage/gen"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/reserve/service/grpc/gen"
)

type OrderService struct {
	orderspb.UnimplementedOrderServiceServer
	db *pgxpool.Pool

	payment   paymentpb.PaymentServiceClient
	reserve   reservepb.ReserveServiceClient
	warehouse reservepb.WarehouseItemServiceClient
}

func NewService(connPool *pgxpool.Pool, payment paymentpb.PaymentServiceClient,
	reserve reservepb.ReserveServiceClient,
	warehouse reservepb.WarehouseItemServiceClient,
) *OrderService {
	return &OrderService{
		db:        connPool,
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
			return nil, status.Errorf(grpc.Status(err), "failed to create order [%s]: %v", orderID, err)
		}

		if delivery := body.Delivery; delivery != nil {
			if err := query.InsertOrUpdateDelivery(ctx, sqlc.InsertOrUpdateDeliveryParams{
				OrderID: orderID,
				Address: delivery.Address,
				Type:    delivery.Type.String(),
			}); err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to create delivery (order [%s], address [%s]): %v", orderID, delivery.Address, err)
			}
		}

		cost := 0.0
		reserveItems := []*reservepb.ReserveCreateRequest_Reserve_Item{}
		for _, item := range body.Items {
			id := item.Id
			itemCost, err := s.warehouse.GetItemCost(ctx, &reservepb.GetItemCostRequest{Id: id})
			if err != nil {
				return nil, status.Errorf(grpc.Status(err), "failed to get item cost (order [%s], item [%s]): %v", orderID, id, err)
			}
			cost += itemCost.GetCost()
			reserveItems = append(reserveItems, &reservepb.ReserveCreateRequest_Reserve_Item{
				Id:     id,
				Amount: item.Amount,
			})
		}

		if resp, err := s.payment.Create(ctx, &paymentpb.PaymentCreateRequest{Body: &paymentpb.PaymentCreateRequest_PaymentCreate{
			ExternalRef: orderID,
			ClientId:    customerId,
			Amount:      cost,
		}}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to create payment (order [%s]): %v", orderID, err)
		} else {
			orderEntity.PaymentID = pg.String(resp.Id)
		}

		if resp, err := s.reserve.Create(ctx, &reservepb.ReserveCreateRequest{Body: &reservepb.ReserveCreateRequest_Reserve{
			ExternalRef: orderID,
			Items:       reserveItems,
		}}); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to create reserve (order [%s]): %v", orderID, err)
		} else {
			orderEntity.ReserveID = pg.String(resp.Id)
		}

		orderEntity.CreatedAt = pg.Timestamptz(time.Now())
		finalStatus := sqlc.OrderStatusCREATED
		orderEntity.Status = finalStatus
		if err := query.InsertOrUpdateOrder(ctx, orderEntity); err != nil {
			return nil, status.Errorf(grpc.Status(err), "failed to update order [%s] by status [%s]: %v", orderID, finalStatus, err)
		}

		return &orderspb.OrderCreateResponse{Id: orderID}, nil
	})
}

func (s *OrderService) Approve(ctx context.Context, req *orderspb.OrderApproveRequest) (*orderspb.OrderApproveResponse, error) {
	query := sqlc.New(s.db)

	row, err := query.FindOrderById(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	intermediateStatus := sqlc.OrderStatusAPPROVING
	finalStatus := sqlc.OrderStatusAPPROVED
	expecteds := slice.Of(sqlc.OrderStatusCREATED, sqlc.OrderStatusINSUFFICIENT, intermediateStatus)

	if err := s.doWorkflow(ctx, query, row.Order, expecteds, intermediateStatus, finalStatus, req.TwoPhaseCommit, s.approve_); err != nil {
		return nil, err
	}

	return &orderspb.OrderApproveResponse{Id: req.Id, Status: toProtoOrderStatus(finalStatus)}, nil
}

func (s *OrderService) Release(ctx context.Context, req *orderspb.OrderReleaseRequest) (*orderspb.OrderReleaseResponse, error) {
	query := sqlc.New(s.db)

	row, err := query.FindOrderById(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	intermediateStatus := sqlc.OrderStatusRELEASING
	finalStatus := sqlc.OrderStatusRELEASED
	expecteds := slice.Of(sqlc.OrderStatusAPPROVED, intermediateStatus)

	if err := s.doWorkflow(ctx, query, row.Order, expecteds, intermediateStatus, finalStatus, req.TwoPhaseCommit, s.release_); err != nil {
		return nil, err
	}
	return &orderspb.OrderReleaseResponse{Id: req.Id, Status: toProtoOrderStatus(finalStatus)}, nil
}

func (s *OrderService) Cancel(ctx context.Context, req *orderspb.OrderCancelRequest) (*orderspb.OrderCancelResponse, error) {
	query := sqlc.New(s.db)

	row, err := query.FindOrderById(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	intermediateStatus := sqlc.OrderStatusCANCELLING
	finalStatus := sqlc.OrderStatusCANCELLED
	expecteds := slice.Of(sqlc.OrderStatusCREATED, sqlc.OrderStatusINSUFFICIENT, sqlc.OrderStatusAPPROVED, intermediateStatus)

	if err := s.doWorkflow(ctx, query, row.Order, expecteds, intermediateStatus, finalStatus, req.TwoPhaseCommit, s.cancel_); err != nil {
		return nil, err
	}

	return &orderspb.OrderCancelResponse{Id: req.Id}, nil
}

func (s *OrderService) Resume(ctx context.Context, req *orderspb.OrderResumeRequest) (*orderspb.OrderResumeResponse, error) {
	return nil, status.Error(codes.Internal, "unimplemented")
	// query := sqlc.New(s.db)

	// order, err := query.FindOrderById(ctx, req.Id)
	// if err != nil {
	// 	return nil, err
	// }

	// if err := checkStatus(order, sqlc.OrderStatusCREATED); err != nil {
	// 	return nil, err
	// }

	// // Update order status back to CREATED
	// _, err = s.db.ExecContext(ctx, `
	// 	UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2`,
	// 	(orders.StatusCreated), req.Id)
	// if err != nil {
	// 	return nil, status.Errorf(grpc.Status(err), "failed to update order status: %w", err)
	// }

	// return &orderspb.OrderResumeResponse{Id: req.Id, Status: *toProtoOrderStatus(orders.StatusCreated)}, nil
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
		return status.Errorf(grpc.Status(err), "payment approving failed (paymentId [%s]): %v", paymentId, err)
	}

	if _, err := s.reserve.Approve(ctx, &reservepb.ReserveApproveRequest{
		Id:                    reserveId,
		PreparedTransactionId: reserveTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "reserve approving failed (reserveId [%s]): %v", reserveId, err)
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
		return status.Errorf(grpc.Status(err), "payment cancelling failed (paymentId [%s]): %v", paymentId, err)
	}

	if _, err := s.reserve.Cancel(ctx, &reservepb.ReserveCancelRequest{
		Id:                    reserveId,
		PreparedTransactionId: reserveTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "reserve cancelling failed (reserveId [%s]): %v", reserveId, err)
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
		return status.Errorf(grpc.Status(err), "payment pay failed (paymentId [%s]): %v", paymentId, err)
	}

	if _, err := s.reserve.Release(ctx, &reservepb.ReserveReleaseRequest{
		Id:                    reserveId,
		PreparedTransactionId: reserveTransactionID(twoPhaseCommit, order),
	}); err != nil {
		return status.Errorf(grpc.Status(err), "reserve release failed (reserveId [%s]): %v", reserveId, err)
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
	return orderspb.Order_Delivery_Type(orderspb.Order_Delivery_Type_value[delivery.Type])
}

func toProtoOrder(order sqlc.Order, delivery sqlc.Delivery) *orderspb.Order {
	// Get order items
	// items, err := s.getOrderItems(ctx, order.ID)
	// if err != nil {
	// 	return nil, err
	// }

	orderProto := &orderspb.Order{
		Id:         order.ID,
		CreatedAt:  timestamppb.New(order.CreatedAt.Time),
		UpdatedAt:  timestamppb.New(order.UpdatedAt.Time),
		CustomerId: order.CustomerID,
		Delivery:   toProtDelivery(delivery),
		// Status:     orderspb.Order_Status(order.Status),
		// Items: items,
	}

	// if order.PaymentID != nil {
	// 	orderProto.PaymentId = order.PaymentID
	// }

	// if order.ReserveID != nil {
	// 	orderProto.ReserveId = order.ReserveID
	// }

	// if order.PaymentStatus != nil {
	// 	paymentStatus := paymentpb.Payment_Status(*order.PaymentStatus)
	// 	orderProto.PaymentStatus = paymentStatus
	// }

	// // Add delivery info
	// orderProto.Delivery = &orderspb.Order_Delivery{
	// 	Address: order.DeliveryAddress,
	// 	Type:    orderspb.Order_Delivery_Type(order.DeliveryType),
	// }

	// if order.DeliveryDate != nil {
	// 	orderProto.Delivery.DateTime = order.ToTimestamp(*order.DeliveryDate)
	// }

	return orderProto
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
		return status.Errorf(grpc.Status(err), "failed to update order status (orderId [%s], status [%s]): %w",
			orderId, finalStatus, err)
	}
	return nil
}
