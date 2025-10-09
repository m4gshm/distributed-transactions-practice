package producer

import (
	"context"
	"encoding/json"
	"fmt"
	
	"github.com/rs/zerolog/log"
	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/twmb/franz-go/plugin/kzerolog"
)

func New[E any](clientID, topic string, servers []string) (*EventKafkaProducer[E], error) {
	client, err := kgo.NewClient(
		kgo.WithLogger(kzerolog.New(&log.Logger)),
		kgo.SeedBrokers(servers...), kgo.AllowAutoTopicCreation(), kgo.ClientID(clientID))
	if err != nil {
		return nil, fmt.Errorf("failed to create kafka client: %w", err)
	}
	return &EventKafkaProducer[E]{topic: topic, client: client}, nil
}

type EventKafkaProducer[E any] struct {
	topic  string
	client *kgo.Client
}

func (e *EventKafkaProducer[E]) Close() {
	e.client.Close()
}

func (e *EventKafkaProducer[E]) Send(ctx context.Context, event E) error {
	eventJSON, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("failed to marshal event to JSON: %w", err)
	}
	result := e.client.ProduceSync(ctx, &kgo.Record{Topic: e.topic, Value: eventJSON})
	return result.FirstErr()
}
