package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.protobuf.TimestampUtils;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders;
import orders.v1.Orders.OrderCreateRequest.OrderCreate;
import payment.v1.PaymentOuterClass.Payment;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.Reserve;
import tpc.v1.Tpc;

import java.time.OffsetDateTime;
import java.util.List;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static io.grpc.Status.NOT_FOUND;
import static java.time.ZoneId.systemDefault;
import static java.util.Optional.ofNullable;
import static orders.v1.Orders.Order.Status.APPROVED;
import static orders.v1.Orders.Order.Status.CANCELLED;
import static orders.v1.Orders.Order.Status.CANCELLING;
import static orders.v1.Orders.Order.Status.CREATED;
import static orders.v1.Orders.Order.Status.INSUFFICIENT;
import static orders.v1.Orders.Order.Status.RELEASED;
import static reactor.core.publisher.Mono.error;

@Slf4j
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

    static Orders.Order toOrderGrpc(
                                    Order order,
                                    Payment.Status paymentStatus,
                                    List<Reserve.Item> items
    ) {
        var builder = Orders.Order.newBuilder()
                .setId(order.id())
                .setCreatedAt(toTimestamp(order.createdAt()))
                .setUpdatedAt(toTimestamp(order.updatedAt()))
                .setCustomerId(order.customerId())
                .setPaymentId(order.paymentId())
                .setReserveId(order.reserveId())
                .mergeDelivery(toDeliveryGrpc(order.delivery()))
                .addAllItems(items != null ? items : List.of());

        ofNullable(toOrderStatusGrpc(order.status())).ifPresent(builder::setStatus);
        ofNullable(paymentStatus).ifPresent(builder::setPaymentStatus);

        return builder.build();
    }

    public static Orders.Order.Status toOrderStatusGrpc(Order.Status status) {
        return status == null ? null : switch (status) {
            case CREATING -> null;
            case CREATED -> CREATED;
            case APPROVING -> CREATED;
            case APPROVED -> APPROVED;
            case RELEASING -> APPROVED;
            case RELEASED -> RELEASED;
            case INSUFFICIENT -> INSUFFICIENT;
            case CANCELLING -> CANCELLING;
            case CANCELLED -> CANCELLED;
        };
    }

    private static Orders.Order.Delivery toDeliveryGrpc(Order.Delivery delivery) {
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
                        .dateTime(OffsetDateTime.ofInstant(
                                TimestampUtils.toInstant(delivery.getDateTime()),
                                systemDefault()
                        ))
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

    public static String string(Object any) {
        return any == null ? null : any.toString();
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
                .setId(order.id())
                .build();
    }

    static Tpc.TwoPhaseCommitRequest newCommitRequest(String id) {
        return Tpc.TwoPhaseCommitRequest.newBuilder().setId(id).build();
    }

    static Tpc.TwoPhaseRollbackRequest newRollbackRequest(String reserveResponse) {
        return Tpc.TwoPhaseRollbackRequest.newBuilder()
                .setId(reserveResponse)
                .build();
    }

    static Order.Status getOrderStatus(Payment.Status paymentStatus, Reserve.Status reserveStatus) {
        final Order.Status status;
        if (paymentStatus == Payment.Status.HOLD && reserveStatus == Reserve.Status.APPROVED) {
            status = Order.Status.APPROVED;
        } else if (paymentStatus == Payment.Status.INSUFFICIENT || reserveStatus == Reserve.Status.INSUFFICIENT) {
            status = Order.Status.INSUFFICIENT;
        } else if (paymentStatus == Payment.Status.PAID && reserveStatus == Reserve.Status.RELEASED) {
            status = Order.Status.RELEASED;
        } else {
            status = null;
        }
        log.info("calc order status {} by payment {} and reserve {}", status, paymentStatus, reserveStatus);
        return status;
    }

}
