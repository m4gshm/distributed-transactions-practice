package io.github.m4gshm.orders.data.model;

import io.github.m4gshm.EnumWithCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

import static io.github.m4gshm.EnumWithCodeUtils.getByCode;


@Valid
@Builder(toBuilder = true)
public record Order(String id,
                    Status status,
                    String customerId,
                    String paymentId,
                    String reserveId,
                    OffsetDateTime createdAt,
                    OffsetDateTime updatedAt,
                    Delivery delivery,
                    List<Item> items
) {
    public enum Status implements EnumWithCode<Status> {
        created,
        approved,
        released,
        insufficient,
        cancelled;

        public static Status byCode(String code) {
            return getByCode(Status.class, code);
        }
    }

    @Builder(toBuilder = true)
    public record Item(String id, int amount) {
    }

    @Builder(toBuilder = true)
    public record Delivery(@NotBlank String address, OffsetDateTime dateTime, Type type) {
        public enum Type implements EnumWithCode<Type> {
            pickup,
            courier;

            public static Type byCode(String code) {
                return getByCode(Type.class, code);
            }

        }
    }

}
