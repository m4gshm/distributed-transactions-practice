package io.github.m4gshm.reactive.idempotent.consumer;

import java.time.OffsetDateTime;

public interface Message {
    String getMessageID();

    String getSubscriberID();

    OffsetDateTime getTimestamp();
}
