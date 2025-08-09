package io.github.m4gshm.payments.data.model;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder(toBuilder = true)
public record Account(String clientId, double amount, double locked, OffsetDateTime updatedAt) {
}
