package io.github.m4gshm.reserve.data.model;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder(toBuilder = true)
public record WarehouseItem(
                            String id,
                            @Positive double unitCost,
                            @Positive int amount,
                            @Positive int reserved,
                            OffsetDateTime updatedAt) {
}
