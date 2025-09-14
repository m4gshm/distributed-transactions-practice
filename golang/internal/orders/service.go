package orders

import (
	"context"
	"database/sql"

	"github.com/google/uuid"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	orderspb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/orders"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/payment"
	reservepb "github.com/m4gshm/distributed-transactions-practice/golang/gen/go/reserve"

	"github.com/m4gshm/distributed-transactions-practice/golang/internal/config"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/models"
)

type Service struct {
	orderspb.UnimplementedOrdersServiceServer
	db  *sql.DB
	cfg *config.Config
}

func NewService(db *sql.DB, cfg *config.Config) *Service {
	return &Service{
		db:  db,
		cfg: cfg,
	}
}

func (s *Service) Create(ctx context.Context, req *orderspb.OrderCreateRequest) (*orderspb.OrderCreateResponse, error) {
	if req.Body == nil {
		return nil, status.Errorf(codes.InvalidArgument, "order body is required")
	}

	orderID := uuid.New().String()

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Insert order
	_, err = tx.ExecContext(ctx, `
		INSERT INTO orders (id, customer_id, status, delivery_type, delivery_address, delivery_date, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, NOW(), NOW())`,
		orderID,
		req.Body.CustomerId,
		int(models.OrderStatusCreating),
		int(req.Body.Delivery.Type),
		req.Body.Delivery.Address,
		req.Body.Delivery.DateTime,
	)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to create order: %v", err)
	}

	// Insert order items
	for _, item := range req.Body.Items {
		_, err = tx.ExecContext(ctx, `
			INSERT INTO order_items (id, order_id, item_id, amount, reserved)
			VALUES ($1, $2, $3, $4, $5)`,
			uuid.New().String(),
			orderID,
			item.Id,
			item.Amount,
			false,
		)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to create order item: %v", err)
		}
	}

	// Update order status to CREATED
	_, err = tx.ExecContext(ctx, `
		UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.OrderStatusCreated), orderID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update order status: %v", err)
	}

	if err = tx.Commit(); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
	}

	return &orderspb.OrderCreateResponse{
		Id: orderID,
	}, nil
}

func (s *Service) Approve(ctx context.Context, req *orderspb.OrderApproveRequest) (*orderspb.OrderApproveResponse, error) {
	// Get order
	order, err := s.getOrderByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Check if order can be approved
	if order.Status != models.OrderStatusCreated && order.Status != models.OrderStatusInsufficient {
		return nil, status.Errorf(codes.FailedPrecondition, "order cannot be approved in current status")
	}

	// Start transaction
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to start transaction: %v", err)
	}
	defer tx.Rollback()

	// Update order status to APPROVING
	_, err = tx.ExecContext(ctx, `
		UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.OrderStatusApproving), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update order status: %v", err)
	}

	// TODO: Call payment and reserve services
	// This is a simplified implementation
	// In a real scenario, you would make gRPC calls to payment and reserve services

	// Update order status to APPROVED
	_, err = tx.ExecContext(ctx, `
		UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.OrderStatusApproved), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update order status: %v", err)
	}

	if err = tx.Commit(); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to commit transaction: %v", err)
	}

	return &orderspb.OrderApproveResponse{
		Id:     req.Id,
		Status: orderspb.Order_Status(models.OrderStatusApproved).Enum(),
	}, nil
}

func (s *Service) Release(ctx context.Context, req *orderspb.OrderReleaseRequest) (*orderspb.OrderReleaseResponse, error) {
	// Get order
	order, err := s.getOrderByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Check if order can be released
	if order.Status != models.OrderStatusApproved {
		return nil, status.Errorf(codes.FailedPrecondition, "order cannot be released in current status")
	}

	// Update order status
	_, err = s.db.ExecContext(ctx, `
		UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.OrderStatusReleased), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update order status: %v", err)
	}

	return &orderspb.OrderReleaseResponse{
		Id:     req.Id,
		Status: orderspb.Order_Status(models.OrderStatusReleased).Enum(),
	}, nil
}

func (s *Service) Cancel(ctx context.Context, req *orderspb.OrderCancelRequest) (*orderspb.OrderCancelResponse, error) {
	// Get order
	order, err := s.getOrderByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Check if order can be cancelled
	if order.Status == models.OrderStatusCancelled {
		return nil, status.Errorf(codes.FailedPrecondition, "order is already cancelled")
	}

	// Update order status
	_, err = s.db.ExecContext(ctx, `
		UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.OrderStatusCancelled), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update order status: %v", err)
	}

	return &orderspb.OrderCancelResponse{
		Id: req.Id,
	}, nil
}

func (s *Service) Resume(ctx context.Context, req *orderspb.OrderResumeRequest) (*orderspb.OrderResumeResponse, error) {
	// Get order
	_, err := s.getOrderByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	// Update order status back to CREATED
	_, err = s.db.ExecContext(ctx, `
		UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2`,
		int(models.OrderStatusCreated), req.Id)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update order status: %v", err)
	}

	return &orderspb.OrderResumeResponse{
		Id:     req.Id,
		Status: orderspb.Order_Status(models.OrderStatusCreated),
	}, nil
}

func (s *Service) Get(ctx context.Context, req *orderspb.OrderGetRequest) (*orderspb.OrderGetResponse, error) {
	order, err := s.getOrderByID(ctx, req.Id)
	if err != nil {
		return nil, err
	}

	orderProto, err := s.orderToProto(ctx, order)
	if err != nil {
		return nil, err
	}

	return &orderspb.OrderGetResponse{
		Order: orderProto,
	}, nil
}

func (s *Service) List(ctx context.Context, req *orderspb.OrderListRequest) (*orderspb.OrderListResponse, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, customer_id, payment_id, reserve_id, status, payment_status, 
			   delivery_type, delivery_address, delivery_date, created_at, updated_at
		FROM orders 
		ORDER BY created_at DESC`)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list orders: %v", err)
	}
	defer rows.Close()

	var orders []*orderspb.Order
	for rows.Next() {
		order := &models.Order{}
		err := rows.Scan(
			&order.ID, &order.CustomerID, &order.PaymentID, &order.ReserveID,
			&order.Status, &order.PaymentStatus, &order.DeliveryType,
			&order.DeliveryAddress, &order.DeliveryDate, &order.CreatedAt, &order.UpdatedAt,
		)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to scan order: %v", err)
		}

		orderProto, err := s.orderToProto(ctx, order)
		if err != nil {
			return nil, err
		}
		orders = append(orders, orderProto)
	}

	return &orderspb.OrderListResponse{
		Orders: orders,
	}, nil
}

func (s *Service) getOrderByID(ctx context.Context, orderID string) (*models.Order, error) {
	order := &models.Order{}
	err := s.db.QueryRowContext(ctx, `
		SELECT id, customer_id, payment_id, reserve_id, status, payment_status,
			   delivery_type, delivery_address, delivery_date, created_at, updated_at
		FROM orders WHERE id = $1`, orderID).Scan(
		&order.ID, &order.CustomerID, &order.PaymentID, &order.ReserveID,
		&order.Status, &order.PaymentStatus, &order.DeliveryType,
		&order.DeliveryAddress, &order.DeliveryDate, &order.CreatedAt, &order.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, status.Errorf(codes.NotFound, "order not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to get order: %v", err)
	}
	return order, nil
}

func (s *Service) orderToProto(ctx context.Context, order *models.Order) (*orderspb.Order, error) {
	// Get order items
	items, err := s.getOrderItems(ctx, order.ID)
	if err != nil {
		return nil, err
	}

	orderProto := &orderspb.Order{
		Id:         order.ID,
		CreatedAt:  order.ToTimestamp(order.CreatedAt),
		UpdatedAt:  order.ToTimestamp(order.UpdatedAt),
		CustomerId: order.CustomerID,
		Status:     orderspb.Order_Status(order.Status),
		Items:      items,
	}

	if order.PaymentID != nil {
		orderProto.PaymentId = order.PaymentID
	}

	if order.ReserveID != nil {
		orderProto.ReserveId = order.ReserveID
	}

	if order.PaymentStatus != nil {
		paymentStatus := paymentpb.Payment_Status(*order.PaymentStatus)
		orderProto.PaymentStatus = &paymentStatus
	}

	// Add delivery info
	orderProto.Delivery = &orderspb.Order_Delivery{
		Address: order.DeliveryAddress,
		Type:    orderspb.Order_Delivery_Type(order.DeliveryType),
	}

	if order.DeliveryDate != nil {
		orderProto.Delivery.DateTime = order.ToTimestamp(*order.DeliveryDate)
	}

	return orderProto, nil
}

func (s *Service) getOrderItems(ctx context.Context, orderID string) ([]*reservepb.Reserve_Item, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT item_id, amount, reserved FROM order_items WHERE order_id = $1`, orderID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to get order items: %v", err)
	}
	defer rows.Close()

	var items []*reservepb.Reserve_Item
	for rows.Next() {
		var itemID string
		var amount int32
		var reserved bool

		err := rows.Scan(&itemID, &amount, &reserved)
		if err != nil {
			return nil, status.Errorf(codes.Internal, "failed to scan order item: %v", err)
		}

		item := &reservepb.Reserve_Item{
			Id:       itemID,
			Amount:   amount,
			Reserved: reserved,
		}
		items = append(items, item)
	}

	return items, nil
}
