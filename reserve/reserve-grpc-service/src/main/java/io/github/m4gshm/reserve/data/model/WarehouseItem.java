package io.github.m4gshm.reserve.data.model;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record WarehouseItem(String id, int amount, int reserved, OffsetDateTime updatedAt) {
}
