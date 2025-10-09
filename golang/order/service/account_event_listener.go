package service

import (
	"context"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/m4gshm/distributed-transactions-practice/golang/internal/kafka/consumer"
	orderspb "github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/gen"
	"github.com/m4gshm/distributed-transactions-practice/golang/order/service/grpc/impl"
	"github.com/m4gshm/distributed-transactions-practice/golang/order/storage/sqlc/gen"
	"github.com/m4gshm/distributed-transactions-practice/golang/payment/event"
	paymentpb "github.com/m4gshm/distributed-transactions-practice/golang/payment/service/grpc/gen"
	"github.com/m4gshm/gollections/slice"
	"github.com/rs/zerolog/log"
)

//go:generate fieldr -type AccountEventListener -out . new-full

func NewAccountEventListener(
	clientID string,
	topics []string,
	servers []string,
	db *pgxpool.Pool,
	orders *impl.OrderService,
	payments paymentpb.PaymentServiceClient,
	twoPhaseCommit bool,
) *AccountEventListener {
	return &AccountEventListener{
		clientID:       clientID,
		topics:         topics,
		servers:        servers,
		db:             db,
		orders:         orders,
		payments:       payments,
		twoPhaseCommit: twoPhaseCommit,
	}
}

type AccountEventListener struct {
	clientID       string
	topics         []string
	servers        []string
	db             *pgxpool.Pool
	orders         *impl.OrderService
	payments       paymentpb.PaymentServiceClient
	twoPhaseCommit bool
}

func (a *AccountEventListener) Listen(ctx context.Context) error {
	events, err := consumer.Consume[event.AccountBalance](ctx, a.clientID, a.topics, a.servers)
	if err != nil {
		return err
	}
	go func() {
		for {
			select {
			case <-ctx.Done():
				err := ctx.Err()
				_ = err
				log.Info().Msg("account balance event listener is finished")
				return
			case e, ok := <-events:
				_ = ok
				if err := a.ProcessEvent(ctx, e); err != nil {
					log.Err(err).Msgf("failed to process account balance event")
				}
			}
		}
	}()
	return nil
}

func (a *AccountEventListener) ProcessEvent(ctx context.Context, e event.AccountBalance) error {
	rows, err := gen.New(a.db).FindOrdersByClientAndStatuses(ctx, gen.FindOrdersByClientAndStatusesParams{
		CustomerID:  e.ClientID,
		Orderstatus: slice.Of((gen.OrderStatusINSUFFICIENT)),
	})
	if err != nil {
		//todo
		return err
	}
	balance := e.Balance
	if len(rows) == 0 {
		log.Debug().Msgf("no incufficient orders to update  (clientID %s, balance %f)", e.ClientID, balance)
	}
	for _, row := range rows {
		order := row.Order
		paymentID := order.PaymentID
		if pResponse, err := a.payments.Get(ctx, &paymentpb.PaymentGetRequest{
			Id: paymentID.String,
		}); err != nil {
			//todo
			return err
		} else if payment := pResponse.GetPayment(); payment != nil && payment.Amount < balance {
			if approv, err := a.orders.Approve(ctx, &orderspb.OrderApproveRequest{
				Id:             order.ID,
				TwoPhaseCommit: a.twoPhaseCommit,
			}); err != nil {
				//todo
				return err
			} else {
				_ = approv
				//log
			}
		}
	}
	return nil
}
