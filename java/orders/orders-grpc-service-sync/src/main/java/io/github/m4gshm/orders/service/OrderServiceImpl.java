package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransactionUtils;
import io.github.m4gshm.storage.PageableReadOperations.Page;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.OrderServiceOuterClass;
import orders.v1.OrderServiceOuterClass.OrderApproveResponse;
import orders.v1.OrderServiceOuterClass.OrderCancelResponse;
import orders.v1.OrderServiceOuterClass.OrderCreateRequest;
import orders.v1.OrderServiceOuterClass.OrderCreateResponse;
import orders.v1.OrderServiceOuterClass.OrderGetResponse;
import orders.v1.OrderServiceOuterClass.OrderListResponse;
import orders.v1.OrderServiceOuterClass.OrderReleaseResponse;
import orders.v1.OrderServiceOuterClass.OrderResumeResponse;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass.Payment;
import payment.v1.PaymentServiceGrpc.PaymentServiceBlockingStub;
import payment.v1.PaymentServiceOuterClass.PaymentCreateRequest;
import payment.v1.PaymentServiceOuterClass.PaymentGetRequest;
import reserve.v1.ReserveOuterClass.Reserve;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceOuterClass.ReserveCreateRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveGetRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveGetResponse;
import tpc.v1.TpcService.TwoPhaseCommitResponse;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceBlockingStub;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.ExceptionUtils.newStatusException;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.APPROVED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.APPROVING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CANCELLING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CREATED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CREATING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.INSUFFICIENT;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.RELEASING;
import static io.github.m4gshm.orders.service.OrderServiceUtils.getOrderStatus;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newCommitRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newOrderResumeResponse;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newPaymentApproveRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newPaymentCancelRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newPaymentPayRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newReserveApproveRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newReserveCancelRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newReserveReleaseRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newRollbackRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.toDelivery;
import static io.github.m4gshm.orders.service.OrderServiceUtils.toOrderCreateResponse;
import static io.github.m4gshm.orders.service.OrderServiceUtils.toOrderGrpc;
import static io.github.m4gshm.orders.service.OrderServiceUtils.toOrderStatusGrpc;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PROTECTED;
import static payment.v1.PaymentOuterClass.Payment.Status.PAID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PROTECTED)
public class OrderServiceImpl implements OrderService {
    OrderStorage orderStorage;
    PreparedTransactionService preparedTransactionService;

    ReserveServiceGrpc.ReserveServiceBlockingStub reserveClient;

    TwoPhaseCommitServiceBlockingStub reserveClientTcp;
    PaymentServiceBlockingStub paymentsClient;
    TwoPhaseCommitServiceBlockingStub paymentsClientTcp;
    ItemService itemService;

    @Override
    public OrderApproveResponse approve(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp(
                "approve",
                orderId,
                twoPhaseCommit,
                Set.of(CREATED, INSUFFICIENT),
                APPROVING,
                order -> {
                    return paymentsClient.approve(newPaymentApproveRequest(order, twoPhaseCommit)).getStatus();
                },
                order -> {
                    return reserveClient.approve(newReserveApproveRequest(order, twoPhaseCommit)).getStatus();
                },
                order -> OrderApproveResponse.newBuilder()
                        .setId(order.id())
                        .setStatus(toOrderStatusGrpc(order.status()))
                        .build()
        );
    }

    @Override
    public OrderCancelResponse cancel(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp(
                "cancel",
                orderId,
                twoPhaseCommit,
                Set.of(CREATED, INSUFFICIENT, APPROVED),
                CANCELLING,
                order -> {
                    return paymentsClient.cancel(newPaymentCancelRequest(order, twoPhaseCommit)).getStatus();
                },
                order -> {
                    return reserveClient.cancel(newReserveCancelRequest(order, twoPhaseCommit)).getStatus();
                },
                order -> OrderServiceOuterClass.OrderCancelResponse.newBuilder()
                        .setId(order.id())
                        .build()
        );
    }

    private TwoPhaseCommitResponse commit(
                                          String operationName,
                                          String transactionId,
                                          TwoPhaseCommitServiceBlockingStub paymentsClientTcp
    ) {
        return paymentsClientTcp.commit(newCommitRequest(transactionId));
//        OrderServiceUtils.completeIfNoTransaction(operationName, e, transactionId)
    }

    private TwoPhaseCommitResponse commitPayment(String transactionId) {
        return commit("paymentsClientTcp::commit", transactionId, paymentsClientTcp);
    }

    private TwoPhaseCommitResponse commitReserve(String transactionId) {
        return commit("reserveClientTcp::commit", transactionId, reserveClientTcp);
    }

    private Order create(Order order, boolean twoPhaseCommit) {
        var orderId = order.id();
        var items = order.items();
        var paymentTransactionId = order.paymentTransactionId();
        var cost = itemService.getSumCost(items);

        var paymentRequestBuilder = PaymentCreateRequest.newBuilder()
                .setBody(PaymentCreateRequest.PaymentCreate.newBuilder()
                        .setExternalRef(orderId)
                        .setClientId(order.customerId())
                        .setAmount(cost)
                        .build());
        ofNullable(paymentTransactionId).ifPresent(paymentRequestBuilder::setPreparedTransactionId);
        var paymentRequest = paymentRequestBuilder.build();
        var paymentResponse = paymentsClient.create(paymentRequest);

        var reserveTransactionId = order.reserveTransactionId();

        var reserveRequestBuilder = ReserveCreateRequest.newBuilder()
                .setBody(ReserveCreateRequest.Reserve.newBuilder()
                        .setExternalRef(orderId)
                        .addAllItems(items.stream()
                                .map(OrderServiceUtils::toCreateReserveItem)
                                .toList())
                        .build());
        ofNullable(reserveTransactionId).ifPresent(reserveRequestBuilder::setPreparedTransactionId);

        var reserveResponse = reserveClient.create(reserveRequestBuilder.build());

        var paymentId = paymentResponse.getId();
        var reserveId = reserveResponse.getId();
        return updateOrderAndCommit(
                twoPhaseCommit,
                order.toBuilder()
                        .status(CREATED)
                        .paymentId(paymentId)
                        .reserveId(reserveId)
                        .build(),
                paymentTransactionId,
                reserveTransactionId
        );
    }

    @Override
    public OrderCreateResponse create(OrderCreateRequest.OrderCreate createRequest, boolean twoPhaseCommit) {
        var orderId = OrderServiceUtils.string(UUID.randomUUID().toString());
        var itemsList = createRequest.getItemsList();

        var items = itemsList.stream().map(OrderServiceUtils::toItem).toList();
        var orderBuilder = Order.builder()
                .id(orderId)
                .status(CREATING)
                .customerId(createRequest.getCustomerId())
                .delivery(createRequest.hasDelivery()
                        ? toDelivery(createRequest.getDelivery())
                        : null)
                .items(items);

        if (twoPhaseCommit) {
            orderBuilder
                    .paymentTransactionId(UUID.randomUUID().toString())
                    .reserveTransactionId(UUID.randomUUID().toString());
        }
        var order = orderStorage.save(orderBuilder.build());
        var createdOrder = create(order, twoPhaseCommit);
        return toOrderCreateResponse(createdOrder);
    }

    private <T> void distributedCommit(
                                       String orderId,
                                       @NonNull String paymentTransactionId,
                                       @NonNull String reserveTransactionId,
                                       T result
    ) {
        reserveClientTcp.commit(newCommitRequest(reserveTransactionId));
        paymentsClientTcp.commit(newCommitRequest(paymentTransactionId));
        preparedTransactionService.commit(orderId);
    }

    private void distributedRollback(
                                     String orderId,
                                     String paymentTransactionId,
                                     String reserveTransactionId,
                                     Throwable result
    ) {
        remoteRollback(paymentTransactionId, reserveTransactionId);
        if (!(result instanceof TwoPhaseTransactionUtils.PrepareTransactionException)) {
            log.debug("no local prepared transaction for rollback");
        }
        preparedTransactionService.rollback(orderId);
    }

    @Override
    public OrderGetResponse get(String orderId) {
        var order = orderStorage.getById(orderId);

        var items = getItems(order.reserveId());
        var paymentStatus = getPaymentStatus(order.paymentId());
        var orderGrpc = toOrderGrpc(order, paymentStatus, items);

        return OrderGetResponse.newBuilder().setOrder(orderGrpc).build();
    }

    private List<Reserve.Item> getItems(String reserveId) {
        var request = ReserveGetRequest.newBuilder()
                .setId(reserveId)
                .build();
        return ofNullable(reserveClient.get(request)).map(ReserveGetResponse::getReserve)
                .map(Reserve::getItemsList)
                .orElse(List.of());
    }

    private Payment.Status getPaymentStatus(String paymentId) {
        var request = PaymentGetRequest.newBuilder()
                .setId(paymentId)
                .build();
        return paymentsClient.get(request).getPayment().getStatus();
    }

    @Override
    public OrderListResponse list(Page page, OrderStatus status) {
        var all = orderStorage.findAll(page, status);
        var orders = all.stream().map(order -> {
            return toOrderGrpc(order, null, null);
        }).toList();
        return OrderListResponse.newBuilder()
                .addAllOrders(orders)
                .build();
    }

    @Override
    public OrderReleaseResponse release(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp(
                "release",
                orderId,
                twoPhaseCommit,
                Set.of(APPROVED),
                RELEASING,
                order -> {
                    paymentsClient.pay(newPaymentPayRequest(order, twoPhaseCommit));
                    return PAID;
                },
                order -> {
                    reserveClient.release(newReserveReleaseRequest(order, twoPhaseCommit));
                    return Reserve.Status.RELEASED;
                },
                order -> OrderReleaseResponse.newBuilder()
                        .setId(order.id())
                        .setStatus(toOrderStatusGrpc(order.status()))
                        .build()
        );
    }

    protected void remoteRollback(@NonNull String paymentTransactionId, @NonNull String reserveTransactionId) {
        reserveClientTcp.rollback(newRollbackRequest(reserveTransactionId));
        paymentsClientTcp.rollback(newRollbackRequest(paymentTransactionId));
    }

    @Override
    public OrderResumeResponse resume(String orderId, boolean twoPhaseCommit) {
        var order = orderStorage.getById(orderId);
        var status = order.status();
        return switch (status) {
            case CREATED, APPROVED, RELEASED, CANCELLED -> throw newStatusException(status.getLiteral(),
                    "already committed status");
            case CREATING -> {
                var o = create(order, twoPhaseCommit);
                yield newOrderResumeResponse(
                        o.id(),
                        toOrderStatusGrpc(o.status()));
            }
            case INSUFFICIENT, APPROVING -> {
                var o = approve(order.id(), twoPhaseCommit);
                yield newOrderResumeResponse(
                        o.getId(),
                        o.getStatus());
            }
            case RELEASING -> {
                var o = release(order.id(), twoPhaseCommit);
                yield newOrderResumeResponse(
                        o.getId(),
                        o.getStatus());
            }
            case CANCELLING -> {
                var o = cancel(order.id(), twoPhaseCommit);
                yield newOrderResumeResponse(
                        o.getId(),
                        o.getStatus());
            }
        };
    }

    protected Order updateOrderAndCommit(
                                         boolean twoPhaseCommit,
                                         Order order,
                                         String paymentTransactionId,
                                         String reserveTransactionId
    ) {
        var orderId = order.id();

        var savedOrder = orderStorage.saveOrderOnly(order);
        if (!twoPhaseCommit) {
            return savedOrder;
        } else {
            try {
                // run distributed transaction
                preparedTransactionService.prepare(orderId);
                distributedCommit(
                        orderId,
                        paymentTransactionId,
                        reserveTransactionId,
                        savedOrder
                );
                return savedOrder;
            } catch (Exception throwable) {
                // rollback distributed transaction on error
                log.error("error on transactional operation with orderId [{}]", orderId, throwable);
                distributedRollback(
                        orderId,
                        paymentTransactionId,
                        reserveTransactionId,
                        throwable
                );
                throw throwable;
            }
        }
    }

    protected <T, PI, PO, RI, RO> T updateOrderOp(
                                                  String opName,
                                                  String orderId,
                                                  boolean twoPhaseCommit,
                                                  Set<OrderStatus> expectedFinal,
                                                  OrderStatus intermediateStatus,
                                                  Function<Order, Payment.Status> paymentOp,
                                                  Function<Order, Reserve.Status> reserveOp,
                                                  Function<Order, T> responseBuilder
    ) {
        var order = orderStorage.getById(orderId);
        checkStatus(opName, "order", orderId, order.status(), expectedFinal, intermediateStatus);

        if (order.status() != intermediateStatus) {
            orderStorage.save(order.toBuilder().status(intermediateStatus).build());
        }

        Payment.Status paymentStatus;
        try {
            paymentStatus = paymentOp.apply(order);
        } catch (Exception e) {
            paymentStatus = OrderServiceUtils.statusError(e, Payment.Status::valueOf);
        }
        Reserve.Status reserveStatus;
        try {
            reserveStatus = reserveOp.apply(order);
        } catch (Exception e) {
            reserveStatus = OrderServiceUtils.statusError(e, Reserve.Status::valueOf);
        }
        log.trace("payment op '{}' result [{}] ", opName, paymentStatus);
        log.trace("reserve op '{}' result [{}] ", opName, reserveStatus);
        log.debug("order {} [{}]", opName, orderId);
        var status = getOrderStatus(paymentStatus, reserveStatus);
        if (status == null) {
            log.info(
                    "order status not changed: order [{}], status [{}]",
                    order.id(),
                    order.status()
            );
            return responseBuilder.apply(order);
        } else {
            log.debug(
                    "order status has been changed: order [{}], status [{}]",
                    order.id(),
                    order.status()
            );
            var orderWithNewStatus = OrderServiceUtils.orderWithStatus(
                    order,
                    status
            );
            if (status == INSUFFICIENT) {
                log.info("abort op '{}' on insufficient status of orderId [{}]", opName, orderId);
                var savedOrder = orderStorage.save(orderWithNewStatus);
                if (twoPhaseCommit) {
                    remoteRollback(order.paymentTransactionId(), order.reserveTransactionId());
                }
                return responseBuilder.apply(savedOrder);
            } else {
                return responseBuilder.apply(updateOrderAndCommit(
                        twoPhaseCommit,
                        orderWithNewStatus,
                        order.paymentTransactionId(),
                        order.reserveTransactionId()
                ));
            }
        }
    }
}
