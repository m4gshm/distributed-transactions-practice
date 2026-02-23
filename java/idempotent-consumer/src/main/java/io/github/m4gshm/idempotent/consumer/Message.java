package io.github.m4gshm.idempotent.consumer;

import java.time.OffsetDateTime;

public interface Message {
    String getMessageID();

    String getSubscriberID();

    OffsetDateTime getTimestamp();
}
