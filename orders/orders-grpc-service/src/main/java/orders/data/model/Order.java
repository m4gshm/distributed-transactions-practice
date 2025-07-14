package orders.data.model;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@Builder
public record Order(String id,
                    String customerId,
                    String paymentId,
                    String reserveId,
                    OffsetDateTime createdAt,
                    OffsetDateTime updatedAt,
                    Delivery delivery,
                    List<Item> items
) {
    @Builder
    public record Item(String id, String name, double cost) {
    }

    @Builder
    public record Delivery(String address, OffsetDateTime dateTime, Type type) {
        public enum Type {
            pickup,
            courier;

            private static final Map<String, Type> byCode = stream(Type.values()).collect(toMap(Type::getCode, e -> e));

            public static Type byCode(String code) {
                return byCode.get(code);
            }

            public String getCode() {
                return name();
            }
        }
    }
}
