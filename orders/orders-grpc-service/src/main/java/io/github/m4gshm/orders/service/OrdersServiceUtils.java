package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.protobuf.TimestampUtils;
import lombok.experimental.UtilityClass;
import orders.v1.Orders;
import orders.v1.Orders.OrderCreateRequest.OrderCreate;
import payment.v1.PaymentOuterClass.Payment;
import payment.v1.PaymentOuterClass.PaymentApproveResponse;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse;
import tpc.v1.Tpc;

import java.time.OffsetDateTime;
import java.util.List;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static io.grpc.Status.NOT_FOUND;
import static java.time.ZoneId.systemDefault;
import static java.util.Optional.ofNullable;
import static orders.v1.Orders.Order.Status.APPROVED;
import static orders.v1.Orders.Order.Status.CANCELLED;
import static orders.v1.Orders.Order.Status.CREATED;
import static orders.v1.Orders.Order.Status.INSUFFICIENT;
import static orders.v1.Orders.Order.Status.RELEASED;
import static reactor.core.publisher.Mono.error;

@UtilityClass
public class OrdersServiceUtils {

    // static Orders.Order toOrder(Order order) {
    // var paymentStatus = toPaymentStatus(order.paymentStatus());
    // return toOrder(order, paymentStatus);
    // }
    //
    // static Orders.Order toOrder(Order order, Payment.Status paymentStatus) {
    // var items =
    // order.items().stream().map(OrdersServiceUtils::toOrderItem).toList();
    // return toOrder(order, paymentStatus, items);
    // }

    static Orders.Order toOrder(Order order,
            Payment.Status paymentStatus,
            List<ReserveOuterClass.Reserve.Item> items) {
        var builder = Orders.Order.newBuilder()
                .setId(order.id())
                .setCreatedAt(toTimestamp(order.createdAt()))
                .setUpdatedAt(toTimestamp(order.updatedAt()))
                .setCustomerId(order.customerId())
                .setPaymentId(order.paymentId())
                .setReserveId(order.reserveId())
                .mergeDelivery(toDelivery(order.delivery()))
                .addAllItems(items != null ? items : List.of());

        ofNullable(toOrderStatus(order.status())).ifPresent(builder::setStatus);
        ofNullable(paymentStatus).ifPresent(builder::setPaymentStatus);

        return builder.build();
    }

    public static Orders.Order.Status toOrderStatus(Order.Status status) {
        return status == null ? null : switch (status) {
            case created -> CREATED;
            case approved -> APPROVED;
            case released -> RELEASED;
            case insufficient -> INSUFFICIENT;
            case cancelled -> CANCELLED;
        };
    }

    private static Orders.Order.Delivery toDelivery(Order.Delivery delivery) {
        return delivery == null ? null
                : Orders.Order.Delivery.newBuilder()
                        .setAddress(delivery.address())
                        .mergeDateTime(toTimestamp(delivery.dateTime()))
                        .setType(toType(delivery.type()))
                        .build();
    }

    static Order.Delivery toDelivery(Orders.Order.Delivery delivery) {
        return delivery == null ? null
                : Order.Delivery.builder()
                        .address(delivery.getAddress())
                        .dateTime(OffsetDateTime.ofInstant(TimestampUtils.toInstant(delivery.getDateTime()),
                                systemDefault()))
                        .type(toDeliveryType(delivery.getType()))
                        .build();
    }

    static Order.Delivery.Type toDeliveryType(Orders.Order.Delivery.Type type) {
        return type == null ? null : switch (type) {
            case PICKUP -> Order.Delivery.Type.pickup;
            case COURIER -> Order.Delivery.Type.courier;
            case UNRECOGNIZED -> null;
        };
    }

    private static Orders.Order.Delivery.Type toType(Order.Delivery.Type type) {
        return type == null ? null : switch (type) {
            case pickup -> Orders.Order.Delivery.Type.PICKUP;
            case courier -> Orders.Order.Delivery.Type.COURIER;
        };
    }

    static <T, ID> Mono<T> notFoundById(ID id) {
        return error(() -> NOT_FOUND.withDescription(String.valueOf(id)).asRuntimeException());
    }

    public static String string(Object orderId) {
        return orderId == null ? null : orderId.toString();
    }

    static Order.Item toItem(OrderCreate.Item item) {
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

    static Order.Status getOrderStatus(PaymentApproveResponse.Status paymentStatus,
            ReserveApproveResponse.Status reserveStatus) {
        if (paymentStatus == PaymentApproveResponse.Status.APPROVED
                && reserveStatus == ReserveApproveResponse.Status.APPROVED) {
            return Order.Status.approved;
        } else if (paymentStatus == PaymentApproveResponse.Status.INSUFFICIENT_AMOUNT
                || reserveStatus == ReserveApproveResponse.Status.INSUFFICIENT_QUANTITY) {
            return Order.Status.insufficient;
        } else {
            throw new IllegalStateException("unexpected payment and reserve statuses: '" + paymentStatus
                    + "','"
                    + reserveStatus
                    + "'");
        }
    }

}
