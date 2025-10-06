package event

import "time"

//go:generate fieldr -type AccountBalance -out . get-set

type AccountBalance struct {
	RequestID string    `json:"requestId"`
	ClientID  string    `json:"clientId"`
	Balance   float64   `json:"balance"`
	Timestamp time.Time `json:"timestamp"`
}

func (a *AccountBalance) GetRequestID() string {
	if a != nil {
		return a.RequestID
	}

	var no string
	return no
}

func (a *AccountBalance) SetRequestID(requestID string) {
	if a != nil {
		a.RequestID = requestID
	}
}

func (a *AccountBalance) GetClientID() string {
	if a != nil {
		return a.ClientID
	}

	var no string
	return no
}

func (a *AccountBalance) SetClientID(clientID string) {
	if a != nil {
		a.ClientID = clientID
	}
}

func (a *AccountBalance) GetBalance() float64 {
	if a != nil {
		return a.Balance
	}

	var no float64
	return no
}

func (a *AccountBalance) SetBalance(balance float64) {
	if a != nil {
		a.Balance = balance
	}
}

func (a *AccountBalance) GetTimestamp() time.Time {
	if a != nil {
		return a.Timestamp
	}

	var no time.Time
	return no
}

func (a *AccountBalance) SetTimestamp(timestamp time.Time) {
	if a != nil {
		a.Timestamp = timestamp
	}
}
