package io.github.m4gshm.orders.service;

import io.github.m4gshm.protobuf.TimestampUtils;
import lombok.experimental.UtilityClass;
import io.github.m4gshm.orders.data.model.Order;
import orders.v1.Orders;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import tpc.v1.Tpc;

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
                .setCreatedAt(TimestampUtils.toTimestamp(order.createdAt()))
                .setUpdatedAt(TimestampUtils.toTimestamp(order.updatedAt()))
                .setPaymentId(toString(order.paymentId()))
                .setReserveId(toString(order.reserveId()))
                .mergeDelivery(toDelivery(order.delivery()))
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
                .mergeDateTime(TimestampUtils.toTimestamp(delivery.dateTime()))
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


    static Order.Delivery toDelivery(Orders.Delivery delivery) {
        return delivery == null ? null : Order.Delivery.builder()
                .address(delivery.getAddress())
                .dateTime(OffsetDateTime.ofInstant(TimestampUtils.toInstant(delivery.getDateTime()), systemDefault()))
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

    static Order.Item toItem(Orders.OrderCreateRequest.OrderBody.Item item) {
        return Order.Item.builder()
                .id(uuid(item.getId()))
                .name(item.getName())
                .cost(item.getCost())
                .build();
    }

     static ReserveOuterClass.ReserveCreateRequest.Reserve.Item toCreateReserveItem(Order.Item item) {
        return ReserveOuterClass.ReserveCreateRequest.Reserve.Item.newBuilder()
                .setId(item.id())
                .build();
    }

    static Orders.OrderCreateResponse toOrderCreateResponse(Order order) {
        return Orders.OrderCreateResponse.newBuilder().setId(string(order.id())).build();
    }

    static Tpc.TwoPhaseCommitRequest newCommitRequest(String id) {
        return Tpc.TwoPhaseCommitRequest.newBuilder().setId(string(id)).build();
    }

    static Tpc.TwoPhaseRollbackRequest newRollbackRequest(String reserveResponse) {
        return Tpc.TwoPhaseRollbackRequest.newBuilder()
                .setId(string(reserveResponse))
                .build();
    }
}
