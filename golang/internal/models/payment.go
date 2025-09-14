package models

import (
	"time"
)

type PaymentStatus int

const (
	PaymentStatusCreated PaymentStatus = iota
	PaymentStatusHold
	PaymentStatusInsufficient
	PaymentStatusPaid
	PaymentStatusCancelled
)

type Payment struct {
	ID           string        `json:"id" db:"id"`
	ExternalRef  string        `json:"external_ref" db:"external_ref"`
	ClientID     string        `json:"client_id" db:"client_id"`
	Amount       float64       `json:"amount" db:"amount"`
	Insufficient *float64      `json:"insufficient" db:"insufficient"`
	Status       PaymentStatus `json:"status" db:"status"`
	CreatedAt    time.Time     `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time     `json:"updated_at" db:"updated_at"`
}

type Account struct {
	ClientID  string    `json:"client_id" db:"client_id"`
	Amount    float64   `json:"amount" db:"amount"`
	Locked    float64   `json:"locked" db:"locked"`
	UpdatedAt time.Time `json:"updated_at" db:"updated_at"`
}

type AccountTransaction struct {
	ID          string    `json:"id" db:"id"`
	ClientID    string    `json:"client_id" db:"client_id"`
	Amount      float64   `json:"amount" db:"amount"`
	Type        string    `json:"type" db:"type"` // "credit", "debit", "lock", "unlock"
	Description string    `json:"description" db:"description"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
}
