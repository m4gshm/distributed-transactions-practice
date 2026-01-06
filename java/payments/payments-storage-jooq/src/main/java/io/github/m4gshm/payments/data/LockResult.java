package io.github.m4gshm.payments.data;

import lombok.Builder;

@Builder
public record LockResult(boolean success, double insufficientAmount) {

}
