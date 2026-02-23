package io.github.m4gshm.reserve.data.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ItemOp(String id, int amount) {
    @Builder
    public record ReserveResult(String id, Integer remainder, boolean reserved) {
    }

    @Builder
    public record Result(String id, Integer remainder) {
    }
}
