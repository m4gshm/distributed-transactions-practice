package models

import (
	"time"
)

type ReserveStatus int

const (
	ReserveStatusCreated ReserveStatus = iota
	ReserveStatusInsufficient
	ReserveStatusApproved
	ReserveStatusReleased
	ReserveStatusCancelled
)

type Reserve struct {
	ID          string        `json:"id" db:"id"`
	ExternalRef string        `json:"external_ref" db:"external_ref"`
	Status      ReserveStatus `json:"status" db:"status"`
	CreatedAt   time.Time     `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time     `json:"updated_at" db:"updated_at"`
}

type ReserveItem struct {
	ID           string `json:"id" db:"id"`
	ReserveID    string `json:"reserve_id" db:"reserve_id"`
	ItemID       string `json:"item_id" db:"item_id"`
	Amount       int32  `json:"amount" db:"amount"`
	Insufficient *int32 `json:"insufficient" db:"insufficient"`
	Reserved     bool   `json:"reserved" db:"reserved"`
}

type WarehouseItem struct {
	ID        string    `json:"id" db:"id"`
	Amount    int32     `json:"amount" db:"amount"`
	Reserved  int32     `json:"reserved" db:"reserved"`
	UnitCost  float64   `json:"unit_cost" db:"unit_cost"`
	UpdatedAt time.Time `json:"updated_at" db:"updated_at"`
}
