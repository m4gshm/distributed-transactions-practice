package io.github.m4gshm.reactive.idempotent.consumer;

import lombok.Builder;
import lombok.Getter;

@Builder
public record MessageImpl(
        @Getter String subscriberID,
        @Getter String messageID)
        implements
        Message {
}
