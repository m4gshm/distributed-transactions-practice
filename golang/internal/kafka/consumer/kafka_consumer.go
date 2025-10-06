package consumer

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/rs/zerolog/log"
	"github.com/twmb/franz-go/pkg/kgo"

	"github.com/m4gshm/gollections/slice"
)

func Consume[T any](ctx context.Context, clientID string, topics, servers []string) (<-chan T, error) {
	out, errs, err := ConsumeErr[T](ctx, clientID, topics, servers)
	if err != nil {
		return nil, err
	}
	go func() {
		for err := range errs {
			log.Err(err).Msg("consume kafka message error")
		}
	}()
	return out, nil
}

func ConsumeErr[T any](ctx context.Context, clientID string, topics, servers []string) (<-chan T, <-chan error, error) {
	client, err := kgo.NewClient(kgo.SeedBrokers(servers...), kgo.AllowAutoTopicCreation(),
		kgo.ConsumeTopics(topics...), kgo.ConsumerGroup(clientID), kgo.ClientID(clientID),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to create kafka consumer: %w", err)
	}
	out := make(chan T)
	errOut := make(chan error, 1)
	go func() {
		log.Debug().Msgf("start kafka consumer %s", clientID)
		for {
			fetches := client.PollFetches(ctx)
			if errs := fetches.Errors(); len(errs) > 0 {
				err := errors.Join(slice.Convert(errs, func(e kgo.FetchError) error { return e.Err })...)
				if err != nil {
					//log
					errOut <- err
				} else {
					log.Error().Msg("received nil error by kafka ")
				}
				break
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
		log.Info().Msgf("finish kafka consumer %s", clientID)
		client.Close()
		close(errOut)
		close(out)
	}()
	return out, errOut, nil
}
