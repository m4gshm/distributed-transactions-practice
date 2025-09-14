package models

import (
	"time"

	"google.golang.org/protobuf/types/known/timestamppb"
)

type OrderStatus int

const (
	OrderStatusCreating OrderStatus = iota
	OrderStatusCreated
	OrderStatusApproving
	OrderStatusApproved
	OrderStatusReleasing
	OrderStatusReleased
	OrderStatusInsufficient
	OrderStatusCancelling
	OrderStatusCancelled
)

type DeliveryType int

const (
	DeliveryTypePickup DeliveryType = iota
	DeliveryTypeCourier
)

type Order struct {
	ID              string       `json:"id" db:"id"`
	CreatedAt       time.Time    `json:"created_at" db:"created_at"`
	UpdatedAt       time.Time    `json:"updated_at" db:"updated_at"`
	CustomerID      string       `json:"customer_id" db:"customer_id"`
	PaymentID       *string      `json:"payment_id" db:"payment_id"`
	ReserveID       *string      `json:"reserve_id" db:"reserve_id"`
	Status          OrderStatus  `json:"status" db:"status"`
	PaymentStatus   *int         `json:"payment_status" db:"payment_status"`
	DeliveryType    DeliveryType `json:"delivery_type" db:"delivery_type"`
	DeliveryDate    *time.Time   `json:"delivery_date" db:"delivery_date"`
	DeliveryAddress string       `json:"delivery_address" db:"delivery_address"`
}

type OrderItem struct {
	ID       string `json:"id" db:"id"`
	OrderID  string `json:"order_id" db:"order_id"`
	ItemID   string `json:"item_id" db:"item_id"`
	Amount   int32  `json:"amount" db:"amount"`
	Reserved bool   `json:"reserved" db:"reserved"`
}

// Helper functions for conversion
func (o *Order) ToTimestamp(t time.Time) *timestamppb.Timestamp {
	return timestamppb.New(t)
}

func StatusToProto(status OrderStatus) int32 {
	return int32(status)
}

func ProtoToStatus(status int32) OrderStatus {
	return OrderStatus(status)
}
