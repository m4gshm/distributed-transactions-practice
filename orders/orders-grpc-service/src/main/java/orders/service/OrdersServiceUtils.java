package orders.service;

import com.google.protobuf.Timestamp;
import lombok.experimental.UtilityClass;
import orders.data.model.Order;
import orders.v1.Orders;
import reactor.core.publisher.Mono;
import reserve.v1.Reserve;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static io.grpc.Status.NOT_FOUND;
import static java.time.ZoneId.systemDefault;
import static java.util.Optional.ofNullable;
import static reactor.core.publisher.Mono.error;

@UtilityClass
public class OrdersServiceUtils {
    static Orders.OrdersResponse toOrdersResponse(List<Order> orders) {
        return Orders.OrdersResponse.newBuilder()
                .addAllOrders(orders.stream().map(OrdersServiceUtils::toOrderResponse).toList())
                .build();
    }

    static Orders.OrderResponse toOrderResponse(Order order) {
        return Orders.OrderResponse.newBuilder()
                .setId(toString(order.id()))
                .setCreatedAt(toTimestamp(order.createdAt()))
                .setUpdatedAt(toTimestamp(order.updatedAt()))
                .setPaymentId(toString(order.paymentId()))
                .setReserveId(toString(order.reserveId()))
                .setDelivery(toDelivery(order.delivery()))
                .addAllItems(order.items().stream().map(OrdersServiceUtils::toOrderItem).toList())
                .build();
    }

    private static Orders.OrderItem toOrderItem(Order.Item item) {
        return item == null ? null : Orders.OrderItem.newBuilder()
                .setId(toString(item.id()))
                .setCost(item.cost())
                .setName(item.name())
                .build();
    }

    private static Orders.Delivery toDelivery(Order.Delivery delivery) {
        return delivery == null ? null : Orders.Delivery.newBuilder()
                .setAddress(delivery.address())
                .setDateTime(toTimestamp(delivery.dateTime()))
                .setType(toType(delivery.type()))
                .build();
    }

    private static Orders.Delivery.Type toType(Order.Delivery.Type type) {
        return type == null ? null : switch (type) {
            case pickup -> Orders.Delivery.Type.TYPE_PICKUP;
            case courier -> Orders.Delivery.Type.TYPE_COURIER;
        };
    }

    private static String toString(Object id) {
        return ofNullable(id).map(Object::toString).orElse(null);
    }

    static <T, ID> Mono<T> notFoundById(ID id) {
        return error(() -> NOT_FOUND.withDescription(String.valueOf(id)).asRuntimeException());
    }

    public static String string(Object orderId) {
        return orderId == null ? null : orderId.toString();
    }

    static String uuid(String uuidString) {
//        return UUID.fromString(uuidString);
        return uuidString;
    }

    static Instant toInstant(Timestamp dateTime) {
        return Instant.ofEpochSecond(dateTime.getSeconds(), dateTime.getNanos());
    }

    private static Timestamp toTimestamp(OffsetDateTime offsetDateTime) {
        return ofNullable(offsetDateTime).map(OffsetDateTime::toInstant).map(OrdersServiceUtils::toTimestamp).orElse(null);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }


    static Order.Delivery toDelivery(Orders.Delivery delivery) {
        return delivery == null ? null : Order.Delivery.builder()
                .address(delivery.getAddress())
                .dateTime(OffsetDateTime.ofInstant(toInstant(delivery.getDateTime()), systemDefault()))
                .type(toDeliveryType(delivery.getType()))
                .build();
    }

    static Order.Delivery.Type toDeliveryType(Orders.Delivery.Type type) {
        return switch (type) {
            case TYPE_PICKUP -> Order.Delivery.Type.pickup;
            case TYPE_COURIER -> Order.Delivery.Type.courier;
            case UNRECOGNIZED -> null;
        };
    }

    static Order.Item toItem(Orders.OrderCreateRequest.Item item) {
        return Order.Item.builder()
                .id(uuid(item.getId()))
                .name(item.getName())
                .cost(item.getCost())
                .build();
    }

    static Reserve.NewReserveRequest.Item toReserveItem(Order.Item item) {
        return Reserve.NewReserveRequest.Item.newBuilder()
                .setId(item.id())
                .build();
    }
}
