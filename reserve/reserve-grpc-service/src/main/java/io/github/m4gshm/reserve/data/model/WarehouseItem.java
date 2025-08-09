package io.github.m4gshm.reserve.data.model;

import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record WarehouseItem(String id,
                            @Positive double unitCost,
                            @Positive int amount,
                            @Positive int reserved,
                            OffsetDateTime updatedAt) {
}
