package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.protobuf.TimestampUtils;
import lombok.experimental.UtilityClass;
import orders.v1.Orders;
import payment.v1.PaymentOuterClass;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import tpc.v1.Tpc;

import java.time.OffsetDateTime;
import java.util.List;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static io.grpc.Status.NOT_FOUND;
import static java.time.ZoneId.systemDefault;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static orders.v1.Orders.Order.Status.*;
import static reactor.core.publisher.Mono.error;

@UtilityClass
public class OrdersServiceUtils {

    static Orders.Order toOrder(Order order) {
        return Orders.Order.newBuilder()
                .setId(toString(order.id()))
                .setStatus(toOrderStatus(order.status()))
                .setCreatedAt(toTimestamp(order.createdAt()))
                .setUpdatedAt(toTimestamp(order.updatedAt()))
                .setPaymentId(toString(order.paymentId()))
                .setReserveId(toString(order.reserveId()))
                .mergeDelivery(toDelivery(order.delivery()))
                .addAllItems(order.items().stream().map(OrdersServiceUtils::toOrderItem).toList())
                .build();
    }

    public static Orders.Order.Status toOrderStatus(Order.Status status) {
        return status == null ? null : switch (status) {
            case created -> STATUS_CREATED;
            case approved -> STATUS_APPROVED;
            case insufficient -> STATUS_INSUFFICIENT;
        };
    }

    private static Orders.Order.Item toOrderItem(Order.Item item) {
        if (item == null) {
            return null;
        } else {
            var builder = Orders.Order.Item.newBuilder()
                    .setId(toString(item.id()))
                    .setAmount(item.amount());
            var insufficient = item.insufficient();
            if (insufficient > 0) {
                builder.setInsufficientAmount(insufficient);
            }
            return builder.build();
        }
    }

    private static Orders.Order.Delivery toDelivery(Order.Delivery delivery) {
        return delivery == null ? null : Orders.Order.Delivery.newBuilder()
                .setAddress(delivery.address())
                .mergeDateTime(toTimestamp(delivery.dateTime()))
                .setType(toType(delivery.type()))
                .build();
    }

    private static Orders.Order.Delivery.Type toType(Order.Delivery.Type type) {
        return type == null ? null : switch (type) {
            case pickup -> Orders.Order.Delivery.Type.TYPE_PICKUP;
            case courier -> Orders.Order.Delivery.Type.TYPE_COURIER;
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


    static Order.Delivery toDelivery(Orders.Order.Delivery delivery) {
        return delivery == null ? null : Order.Delivery.builder()
                .address(delivery.getAddress())
                .dateTime(OffsetDateTime.ofInstant(TimestampUtils.toInstant(delivery.getDateTime()), systemDefault()))
                .type(toDeliveryType(delivery.getType()))
                .build();
    }

    static Order.Delivery.Type toDeliveryType(Orders.Order.Delivery.Type type) {
        return switch (type) {
            case TYPE_PICKUP -> Order.Delivery.Type.pickup;
            case TYPE_COURIER -> Order.Delivery.Type.courier;
            case UNRECOGNIZED -> null;
        };
    }

    static Order.Item toItem(Orders.OrderCreateRequest.OrderBody.Item item) {

        return Order.Item.builder()
                .id(item.getId())
                .amount(item.getAmount())
                .build();
    }

    static ReserveOuterClass.ReserveCreateRequest.Reserve.Item toCreateReserveItem(Order.Item item) {
        return ReserveOuterClass.ReserveCreateRequest.Reserve.Item.newBuilder()
                .setId(item.id())
                .setAmount(item.amount())
                .build();
    }

    static Orders.OrderCreateResponse toOrderCreateResponse(Order order) {
        return Orders.OrderCreateResponse.newBuilder()
                .setId(string(order.id()))
                .build();
    }

    static Tpc.TwoPhaseCommitRequest newCommitRequest(String id) {
        return Tpc.TwoPhaseCommitRequest.newBuilder().setId(string(id)).build();
    }

    static Tpc.TwoPhaseRollbackRequest newRollbackRequest(String reserveResponse) {
        return Tpc.TwoPhaseRollbackRequest.newBuilder()
                .setId(string(reserveResponse))
                .build();
    }

    static List<Order.Item> populateItemStatus(Order order, ReserveOuterClass.ReserveApproveResponse reserve) {
        var reserveItemPerId = reserve.getItemsList().stream()
                .collect(toMap(ReserveOuterClass.ReserveApproveResponse.Item::getId, identity()));

        return order.items().stream().map(item -> {
            var itemReserve = reserveItemPerId.get(item.id());
            if (itemReserve != null) {
                return item.toBuilder().insufficient(itemReserve.getInsufficientQuantity())
                        .status(toItemStatus(itemReserve.getStatus())).build();
            } else {
                return item;
            }
        }).toList();
    }

    private static Order.Item.Status toItemStatus(ReserveOuterClass.Reserve.Item.Status status) {
        return switch (status) {
            case RESERVED -> Order.Item.Status.reserved;
            case INSUFFICIENT_QUANTITY -> Order.Item.Status.insufficient_quantity;
            case UNRECOGNIZED -> null;
        };
    }

    static Order.Status getOrderStatus(PaymentOuterClass.PaymentApproveResponse.Status paymentStatus,
                                       ReserveOuterClass.ReserveApproveResponse.Status reserveStatus) {
        if (paymentStatus == PaymentOuterClass.PaymentApproveResponse.Status.APPROVED
                && reserveStatus == ReserveOuterClass.ReserveApproveResponse.Status.APPROVED) {
            return Order.Status.approved;
        } else if (paymentStatus == PaymentOuterClass.PaymentApproveResponse.Status.INSUFFICIENT_FUNDS
                || reserveStatus == ReserveOuterClass.ReserveApproveResponse.Status.INSUFFICIENT_QUANTITY) {
            return Order.Status.insufficient;
        } else {
            throw new IllegalStateException("unexpected payment and reserve statuses: '" + paymentStatus + "','" + reserveStatus + "'");
        }
    }

}
