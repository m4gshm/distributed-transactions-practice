package io.github.m4gshm.payments.event.model;

import lombok.Builder;

@Builder
public record AccountBalanceEvent(String requestId, String clientId, double balance) {
}
