package io.github.m4gshm.orders.service;

import io.github.m4gshm.UnexpectedEntityStatusException;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransaction;
import io.github.m4gshm.protobuf.TimestampUtils;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import orders.v1.OrderOuterClass;
import orders.v1.OrderServiceOuterClass.OrderCreateRequest.OrderCreate;
import orders.v1.OrderServiceOuterClass.OrderCreateResponse;
import orders.v1.OrderServiceOuterClass.OrderResumeResponse;
import org.jooq.DSLContext;
import payment.v1.PaymentOuterClass.Payment;
import payment.v1.PaymentServiceOuterClass.PaymentApproveRequest;
import payment.v1.PaymentServiceOuterClass.PaymentCancelRequest;
import payment.v1.PaymentServiceOuterClass.PaymentPayRequest;
import reactor.core.publisher.Mono;
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

import static io.github.m4gshm.orders.data.model.Order.Delivery.Type.courier;
import static io.github.m4gshm.orders.data.model.Order.Delivery.Type.pickup;
import static io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransaction.rollback;
import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static io.grpc.Status.NOT_FOUND;
import static java.time.ZoneId.systemDefault;
import static java.util.Optional.ofNullable;
import static orders.v1.OrderOuterClass.Order.Status.APPROVED;
import static orders.v1.OrderOuterClass.Order.Status.APPROVING;
import static orders.v1.OrderOuterClass.Order.Status.CANCELLED;
import static orders.v1.OrderOuterClass.Order.Status.CANCELLING;
import static orders.v1.OrderOuterClass.Order.Status.CREATED;
import static orders.v1.OrderOuterClass.Order.Status.CREATING;
import static orders.v1.OrderOuterClass.Order.Status.INSUFFICIENT;
import static orders.v1.OrderOuterClass.Order.Status.RELEASED;
import static orders.v1.OrderOuterClass.Order.Status.RELEASING;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

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
                .setUpdatedAt(toTimestamp(order.updatedAt()))
                .setCustomerId(order.customerId())
                .mergeDelivery(toDeliveryGrpc(order.delivery()))
                .addAllItems(items != null ? items : List.of());

        ofNullable(toOrderStatusGrpc(order.status())).ifPresent(builder::setStatus);
        ofNullable(paymentStatus).ifPresent(builder::setPaymentStatus);
        ofNullable(order.paymentId()).ifPresent(builder::setPaymentId);
        ofNullable(order.reserveId()).ifPresent(builder::setReserveId);

        return builder.build();
    }

    public static OrderOuterClass.Order.Status toOrderStatusGrpc(Order.Status status) {
        return status == null ? null : switch (status) {
            case CREATING -> CREATING;
            case CREATED -> CREATED;
            case APPROVING -> APPROVING;
            case APPROVED -> APPROVED;
            case RELEASING -> RELEASING;
            case RELEASED -> RELEASED;
            case INSUFFICIENT -> INSUFFICIENT;
            case CANCELLING -> CANCELLING;
            case CANCELLED -> CANCELLED;
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

    static Order.Delivery.Type toDeliveryType(OrderOuterClass.Order.Delivery.Type type) {
        return type == null ? null : switch (type) {
            case PICKUP -> pickup;
            case COURIER -> courier;
            case UNRECOGNIZED -> null;
        };
    }

    private static OrderOuterClass.Order.Delivery.Type toType(Order.Delivery.Type type) {
        return type == null ? null : switch (type) {
            case pickup -> OrderOuterClass.Order.Delivery.Type.PICKUP;
            case courier -> OrderOuterClass.Order.Delivery.Type.COURIER;
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

    static boolean completeIfNoTransaction(String type, Throwable e, String transactionId) {
        var status = getStatus(e);
        var noTransaction = status != null && status.getCode().equals(NOT_FOUND.getCode());
        if (noTransaction) {
            logNoTransaction(type, transactionId);
        }
        return noTransaction;
    }

    private static <T> T getCurrentStatusFromError(Function<String, T> converter, Throwable e) {
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

    static Mono<Void> localRollback(DSLContext dsl, String orderTransactionId, Throwable result) {
        return result instanceof TwoPhaseTransaction.PrepareTransactionException
                ? rollback(dsl, orderTransactionId)
                : Mono.<Void>empty().doOnSubscribe(_ -> {
                    log.debug("no local prepared transaction for rollback");
                });
    }

    static void logNoTransaction(String type, String transactionId) {
        log.trace("not transaction for {} {}", type, transactionId);
    }

    public static Order orderWithStatus(Order order, Order.Status status) {
        return order.toBuilder()
                .status(status)
                .build();
    }

    static <T> Function<Throwable, Mono<T>> statusError(Function<String, T> converter) {
        return e -> {
            var status = getCurrentStatusFromError(converter, e);
            if (status != null) {
                log.debug("already in status {}", status);
                return just(status);
            }
            return error(e);
        };
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

    public record ErrorInfo(Status status, Metadata metadata) {
    }
}
