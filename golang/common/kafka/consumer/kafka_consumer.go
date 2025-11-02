package consumer

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/rs/zerolog/log"
	"github.com/twmb/franz-go/pkg/kerr"
	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/twmb/franz-go/plugin/kzerolog"

	"github.com/m4gshm/gollections/slice"
)

func Consume[T any](ctx context.Context, clientID string, topics, servers []string) (<-chan T, error) {
	out, errs, err := ConsumeErr[T](ctx, clientID, topics, servers)
	if err != nil {
		return nil, err
	}
	go func() {
		for err := range errs {
			log.Err(err).Msg("kafka listener consume error")
		}
	}()
	return out, nil
}

func ConsumeErr[T any](ctx context.Context, clientID string, topics, servers []string) (<-chan T, <-chan error, error) {
	out := make(chan T)
	errOut := make(chan error, 1)

	client, err := newConsumer(ctx, servers, topics, clientID)
	if err != nil {
		return nil, nil, err
	}

	go func() {
		log.Info().Msgf("start kafka consumer %s", clientID)
		defer func() {
			close(errOut)
			close(out)
		}()
	loop:
		for {
			select {
			case <-ctx.Done():
				if err := ctx.Err(); err != nil {
					log.Err(err).Msg("abort kafka listener")
				} else {
					log.Info().Msg("abort kafka listener")
				}
				break loop
			default:
				fetches := client.PollFetches(ctx)
				if errs := fetches.Errors(); len(errs) > 0 {
					if e := errors.Join(slice.Convert(errs, func(e kgo.FetchError) error { return e.Err })...); e != nil {
						if errors.Is(e, kerr.UnknownMemberID) || errors.Is(e, kerr.UnknownTopicID) {
							log.Err(e).Msgf("reconnect kafka consumer")
							client.Close()
							if client, err = newConsumer(ctx, servers, topics, clientID); err != nil {
								errOut <- err
								break loop
							}
						} else {
							errOut <- e
						}
					} else {
						log.Error().Msg("kafka listener consume nil error")
					}
				} else {
					iter := fetches.RecordIter()
					for !iter.Done() {
						record := iter.Next()
						var t T
						if err := json.Unmarshal(record.Value, &t); err != nil {
							errOut <- err
							break
						}
						out <- t
					}
				}
			}
		}
		log.Info().Msgf("finish kafka consumer %s", clientID)
		client.Close()
	}()
	return out, errOut, nil
}

func newConsumer(ctx context.Context, servers []string, topics []string, clientID string) (*kgo.Client, error) {
	client, err := kgo.NewClient(
		kgo.WithLogger(kzerolog.New(&log.Logger)),
		kgo.SeedBrokers(servers...), kgo.AllowAutoTopicCreation(),
		kgo.ConsumeTopics(topics...), kgo.ConsumerGroup(clientID), kgo.ClientID(clientID),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create kafka consumer: %w", err)
	} else if err = client.Ping(ctx); err != nil {
		return nil, fmt.Errorf("failed to ping kafka: %w", err)
	}
	return client, err
}
