package io.github.m4gshm.payments.event.model;

import java.time.OffsetDateTime;

import lombok.Builder;

@Builder
public record AccountBalanceEvent(String requestId, String clientId, double balance, OffsetDateTime timestamp) {
}
