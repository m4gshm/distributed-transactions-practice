package io.github.m4gshm.orders.data.model;

import io.github.m4gshm.orders.data.access.jooq.enums.DeliveryType;
import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Valid
@Builder(toBuilder = true)
public record Order(
                    String id,
                    OrderStatus status,
                    String customerId,
                    String paymentId,
                    String reserveId,
                    OffsetDateTime createdAt,
                    OffsetDateTime updatedAt,
                    Delivery delivery,
                    List<Item> items,
                    String paymentTransactionId,
                    String reserveTransactionId
) {

    @Builder(toBuilder = true)
    public record Item(String id, int amount) {
    }

    @Builder(toBuilder = true)
    public record Delivery(@NotBlank String address, OffsetDateTime dateTime, DeliveryType type) {
    }

}
