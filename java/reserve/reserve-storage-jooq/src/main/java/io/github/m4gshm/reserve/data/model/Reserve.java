package io.github.m4gshm.reserve.data.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import org.springframework.validation.annotation.Validated;
import reserve.data.access.jooq.enums.ReserveStatus;

import java.time.OffsetDateTime;
import java.util.List;

@Validated
@Valid
@Builder(toBuilder = true)
public record Reserve(
                      String id,
                      String externalRef,
                      ReserveStatus status,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt,
                      @Valid List<Item> items
) {

    @Builder(toBuilder = true)
    public record Item(String id, @Positive Integer amount, Integer insufficient, boolean reserved) {
    }
}
