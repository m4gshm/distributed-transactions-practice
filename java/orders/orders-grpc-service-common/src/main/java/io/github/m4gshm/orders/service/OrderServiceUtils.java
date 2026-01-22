package io.github.m4gshm.orders.service;

import io.github.m4gshm.UnexpectedEntityStatusException;
import io.github.m4gshm.orders.data.access.jooq.enums.DeliveryType;
import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.protobuf.TimestampUtils;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import orders.v1.OrderOuterClass;
import orders.v1.OrderServiceOuterClass;
import orders.v1.OrderServiceOuterClass.OrderCreateRequest.OrderCreate;
import orders.v1.OrderServiceOuterClass.OrderCreateResponse;
import orders.v1.OrderServiceOuterClass.OrderResumeResponse;
import payment.v1.PaymentOuterClass.Payment;
import payment.v1.PaymentServiceOuterClass.PaymentApproveRequest;
import payment.v1.PaymentServiceOuterClass.PaymentCancelRequest;
import payment.v1.PaymentServiceOuterClass.PaymentPayRequest;
import reserve.v1.ReserveOuterClass.Reserve;
import reserve.v1.ReserveServiceOuterClass.ReserveApproveRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveCancelRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveCreateRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveReleaseRequest;
import tpc.v1.TpcService.TwoPhaseCommitRequest;
import tpc.v1.TpcService.TwoPhaseRollbackRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.APPROVED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.APPROVING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CANCELLED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CANCELLING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CREATED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CREATING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.INSUFFICIENT;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.RELEASED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.RELEASING;
import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static io.grpc.Status.NOT_FOUND;
import static java.time.ZoneId.systemDefault;
import static java.util.Optional.ofNullable;

@Slf4j
@UtilityClass
public class OrderServiceUtils {

    static OrderOuterClass.Order toOrderGrpc(
                                             Order order,
                                             Payment.Status paymentStatus,
                                             List<Reserve.Item> items
    ) {
        var builder = OrderOuterClass.Order.newBuilder()
                .setId(order.id())
                .setCreatedAt(toTimestamp(order.createdAt()))
                .setCustomerId(order.customerId())
                .addAllItems(items != null ? items : List.of())
                .mergeUpdatedAt(toTimestamp(order.updatedAt()))
                .mergeDelivery(toDeliveryGrpc(order.delivery()));

        ofNullable(toOrderStatusGrpc(order.status())).ifPresent(builder::setStatus);
        ofNullable(paymentStatus).ifPresent(builder::setPaymentStatus);
        ofNullable(order.paymentId()).ifPresent(builder::setPaymentId);
        ofNullable(order.reserveId()).ifPresent(builder::setReserveId);

        return builder.build();
    }

    public static OrderStatus toOrderStatus(OrderOuterClass.Order.Status status) {
        return status == null ? null : switch (status) {
            case OrderOuterClass.Order.Status.CREATING -> CREATING;
            case OrderOuterClass.Order.Status.CREATED -> CREATED;
            case OrderOuterClass.Order.Status.APPROVING -> APPROVING;
            case OrderOuterClass.Order.Status.APPROVED -> APPROVED;
            case OrderOuterClass.Order.Status.RELEASING -> RELEASING;
            case OrderOuterClass.Order.Status.RELEASED -> RELEASED;
            case OrderOuterClass.Order.Status.INSUFFICIENT -> INSUFFICIENT;
            case OrderOuterClass.Order.Status.CANCELLING -> CANCELLING;
            case OrderOuterClass.Order.Status.CANCELLED -> CANCELLED;
            case UNRECOGNIZED -> null;
        };
    }

    public static OrderOuterClass.Order.Status toOrderStatusGrpc(OrderStatus status) {
        return status == null ? null : switch (status) {
            case CREATING -> OrderOuterClass.Order.Status.CREATING;
            case CREATED -> OrderOuterClass.Order.Status.CREATED;
            case APPROVING -> OrderOuterClass.Order.Status.APPROVING;
            case APPROVED -> OrderOuterClass.Order.Status.APPROVED;
            case RELEASING -> OrderOuterClass.Order.Status.RELEASING;
            case RELEASED -> OrderOuterClass.Order.Status.RELEASED;
            case INSUFFICIENT -> OrderOuterClass.Order.Status.INSUFFICIENT;
            case CANCELLING -> OrderOuterClass.Order.Status.CANCELLING;
            case CANCELLED -> OrderOuterClass.Order.Status.CANCELLED;
        };
    }

    private static OrderOuterClass.Order.Delivery toDeliveryGrpc(Order.Delivery delivery) {
        return delivery == null ? null
                : OrderOuterClass.Order.Delivery.newBuilder()
                        .setAddress(delivery.address())
                        .mergeDateTime(toTimestamp(delivery.dateTime()))
                        .setType(toType(delivery.type()))
                        .build();
    }

    static Order.Delivery toDelivery(OrderOuterClass.Order.Delivery delivery) {
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

    static DeliveryType toDeliveryType(OrderOuterClass.Order.Delivery.Type type) {
        return type == null ? null : switch (type) {
            case PICKUP -> DeliveryType.PICKUP;
            case COURIER -> DeliveryType.COURIER;
            case UNRECOGNIZED -> null;
        };
    }

    private static OrderOuterClass.Order.Delivery.Type toType(DeliveryType type) {
        return type == null ? null : switch (type) {
            case PICKUP -> OrderOuterClass.Order.Delivery.Type.PICKUP;
            case COURIER -> OrderOuterClass.Order.Delivery.Type.COURIER;
        };
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

    static ReserveCreateRequest.Reserve.Item toCreateReserveItem(Order.Item item) {
        return ReserveCreateRequest.Reserve.Item.newBuilder()
                .setId(item.id())
                .setAmount(item.amount())
                .build();
    }

    static OrderCreateResponse toOrderCreateResponse(Order order) {
        return OrderCreateResponse.newBuilder()
                .setId(order.id())
                .build();
    }

    static TwoPhaseCommitRequest newCommitRequest(@NonNull String transactionId) {
        if (transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId cannot be blank");
        }
        return TwoPhaseCommitRequest.newBuilder().setId(transactionId).build();
    }

    static TwoPhaseRollbackRequest newRollbackRequest(String reserveResponse) {
        return TwoPhaseRollbackRequest.newBuilder()
                .setId(reserveResponse)
                .build();
    }

    static OrderStatus getOrderStatus(Payment.Status paymentStatus, Reserve.Status reserveStatus) {
        final OrderStatus status;
        if (paymentStatus == Payment.Status.HOLD && reserveStatus == Reserve.Status.APPROVED) {
            status = APPROVED;
        } else if (paymentStatus == Payment.Status.INSUFFICIENT || reserveStatus == Reserve.Status.INSUFFICIENT) {
            status = INSUFFICIENT;
        } else if (paymentStatus == Payment.Status.PAID && reserveStatus == Reserve.Status.RELEASED) {
            status = RELEASED;
        } else {
            status = null;
        }
        log.debug("calc order status {} by payment {} and reserve {}", status, paymentStatus, reserveStatus);
        return status;
    }

    static boolean completeIfNoTransaction(String type, Throwable e, String transactionId) {
        var status = getStatus(e);
        var noTransaction = status != null && status.getCode().equals(NOT_FOUND.getCode());
        if (noTransaction) {
            logNoTransaction(type, transactionId);
        }
        return noTransaction;
    }

    public static <T> T getCurrentStatusFromError(Function<String, T> converter, Throwable e) {
        return ofNullable(getErrorInfo(e)).map(ErrorInfo::metadata).map(metadata -> {
            var errorType = metadata.get(UnexpectedEntityStatusException.TYPE);
            return UnexpectedEntityStatusException.class.getSimpleName()
                    .equals(errorType) ? converter.apply(metadata.get(
                            UnexpectedEntityStatusException.STATUS)) : null;
        }).orElse(null);
    }

    private static ErrorInfo getErrorInfo(Throwable e) {
        final Status status;
        final Metadata metadata;
        if (e instanceof StatusRuntimeException exception) {
            status = exception.getStatus();
            metadata = exception.getTrailers();
        } else if (e instanceof StatusException exception) {
            status = exception.getStatus();
            metadata = exception.getTrailers();
        } else {
            status = null;
            metadata = null;
        }
        return status != null ? new ErrorInfo(status, metadata) : null;
    }

    private static Status getStatus(Throwable e) {
        var errorInfo = getErrorInfo(e);
        return errorInfo != null ? errorInfo.status() : null;
    }

    static void logNoTransaction(String type, String transactionId) {
        log.trace("not transaction for {} {}", type, transactionId);
    }

    public static Order orderWithStatus(Order order, OrderStatus status) {
        return order.toBuilder()
                .status(status)
                .build();
    }

    @SneakyThrows
    static <T> T statusError(Exception e, Function<String, T> converter) {
        var status = getCurrentStatusFromError(converter, e);
        if (status != null) {
            log.debug("already in status {}", status);
            return status;
        }
        throw e;
    }

    static OrderResumeResponse newOrderResumeResponse(String o, orders.v1.OrderOuterClass.Order.Status o1) {
        return OrderResumeResponse
                .newBuilder()
                .setId(o)
                .setStatus(o1)
                .build();
    }

    private static <B, T> T newRequest(boolean twoPhaseCommit,
                                       String id,
                                       String transactionId,
                                       B builder,
                                       BiConsumer<B, String> setId,
                                       BiConsumer<B, String> setPreparedTransactionId,
                                       Function<B, T> build
    ) {
        setId.accept(builder, id);
        if (twoPhaseCommit) {
            setPreparedTransactionId.accept(builder, transactionId);
        }
        return build.apply(builder);
    }

    static ReserveApproveRequest newReserveApproveRequest(Order order, boolean twoPhaseCommit) {
        return newRequest(
                twoPhaseCommit,
                order.reserveId(),
                order.reserveTransactionId(),
                ReserveApproveRequest.newBuilder(),
                ReserveApproveRequest.Builder::setId,
                ReserveApproveRequest.Builder::setPreparedTransactionId,
                ReserveApproveRequest.Builder::build
        );
    }

    static ReserveCancelRequest newReserveCancelRequest(Order order, boolean twoPhaseCommit) {
        return newRequest(
                twoPhaseCommit,
                order.reserveId(),
                order.reserveTransactionId(),
                ReserveCancelRequest.newBuilder(),
                ReserveCancelRequest.Builder::setId,
                ReserveCancelRequest.Builder::setPreparedTransactionId,
                ReserveCancelRequest.Builder::build
        );
    }

    static PaymentCancelRequest newPaymentCancelRequest(Order order, boolean twoPhaseCommit) {
        return newRequest(
                twoPhaseCommit,
                order.paymentId(),
                order.paymentTransactionId(),
                PaymentCancelRequest.newBuilder(),
                PaymentCancelRequest.Builder::setId,
                PaymentCancelRequest.Builder::setPreparedTransactionId,
                PaymentCancelRequest.Builder::build
        );
    }

    static ReserveReleaseRequest newReserveReleaseRequest(Order order, boolean twoPhaseCommit) {
        return newRequest(
                twoPhaseCommit,
                order.reserveId(),
                order.reserveTransactionId(),
                ReserveReleaseRequest.newBuilder(),
                ReserveReleaseRequest.Builder::setId,
                ReserveReleaseRequest.Builder::setPreparedTransactionId,
                ReserveReleaseRequest.Builder::build
        );
    }

    static PaymentPayRequest newPaymentPayRequest(Order order, boolean twoPhaseCommit) {
        return newRequest(
                twoPhaseCommit,
                order.paymentId(),
                order.paymentTransactionId(),
                PaymentPayRequest.newBuilder(),
                PaymentPayRequest.Builder::setId,
                PaymentPayRequest.Builder::setPreparedTransactionId,
                PaymentPayRequest.Builder::build
        );
    }

    static PaymentApproveRequest newPaymentApproveRequest(Order order, boolean twoPhaseCommit) {
        return newRequest(
                twoPhaseCommit,
                order.paymentId(),
                order.paymentTransactionId(),
                PaymentApproveRequest.newBuilder(),
                PaymentApproveRequest.Builder::setId,
                PaymentApproveRequest.Builder::setPreparedTransactionId,
                PaymentApproveRequest.Builder::build
        );
    }

    static io.github.m4gshm.storage.Page toPage(OrderServiceOuterClass.Page page) {
        return page == null ? null : new io.github.m4gshm.storage.Page(page.getNum(), page.getSize());
    }

    public record ErrorInfo(Status status, Metadata metadata) {
    }
}
