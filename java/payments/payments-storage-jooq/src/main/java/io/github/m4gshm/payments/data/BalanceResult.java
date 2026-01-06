package io.github.m4gshm.payments.data;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record BalanceResult(double balance, OffsetDateTime timestamp) {

}
