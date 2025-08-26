package io.github.m4gshm.orders.service;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.ExceptionUtils.newStatusException;
import static io.github.m4gshm.orders.data.model.Order.Status.APPROVED;
import static io.github.m4gshm.orders.data.model.Order.Status.APPROVING;
import static io.github.m4gshm.orders.data.model.Order.Status.CANCELLING;
import static io.github.m4gshm.orders.data.model.Order.Status.CREATED;
import static io.github.m4gshm.orders.data.model.Order.Status.CREATING;
import static io.github.m4gshm.orders.data.model.Order.Status.INSUFFICIENT;
import static io.github.m4gshm.orders.data.model.Order.Status.RELEASING;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.getOrderStatus;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.newRollbackRequest;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toDelivery;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toOrderGrpc;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toOrderStatusGrpc;
import static io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PROTECTED;
import static payment.v1.PaymentOuterClass.Payment.Status.PAID;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.fromSupplier;
import static reactor.core.publisher.Mono.just;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransaction;
import io.github.m4gshm.storage.NotFoundException;
import io.github.m4gshm.utils.Jooq;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders.OrderApproveResponse;
import orders.v1.Orders.OrderCancelResponse;
import orders.v1.Orders.OrderCreateRequest;
import orders.v1.Orders.OrderCreateResponse;
import orders.v1.Orders.OrderGetResponse;
import orders.v1.Orders.OrderListResponse;
import orders.v1.Orders.OrderReleaseResponse;
import orders.v1.Orders.OrderResumeResponse;
import payment.v1.PaymentOuterClass;
import payment.v1.PaymentOuterClass.Payment;
import payment.v1.PaymentOuterClass.PaymentApproveResponse;
import payment.v1.PaymentServiceGrpc;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.Reserve;
import reserve.v1.ReserveOuterClass.ReserveApproveResponse;
import reserve.v1.ReserveServiceGrpc;
import tpc.v1.Tpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PROTECTED)
public class OrdersServiceImpl implements OrdersService {
    OrderStorage orderStorage;
    PreparedTransactionService localPreparedTransactions;

    ReserveServiceGrpc.ReserveServiceStub reserveClient;

    TwoPhaseCommitServiceStub reserveClientTcp;
    PaymentServiceGrpc.PaymentServiceStub paymentsClient;
    TwoPhaseCommitServiceStub paymentsClientTcp;
    ItemService itemService;
    Jooq jooq;

    @Override
    public Mono<OrderApproveResponse> approve(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp(
                "approve",
                orderId,
                twoPhaseCommit,
                Set.of(CREATED, INSUFFICIENT),
                APPROVING,
                order -> toMono(
                        "paymentsClient::approve",
                        OrdersServiceUtils.newPaymentApproveRequest(order, twoPhaseCommit),
                        paymentsClient::approve
                ).map(PaymentApproveResponse::getStatus),
                order -> toMono(
                        "reserveClient::approve",
                        OrdersServiceUtils.newReserveApproveRequest(order, twoPhaseCommit),
                        reserveClient::approve
                ).map(ReserveApproveResponse::getStatus),
                order -> OrderApproveResponse.newBuilder()
                        .setId(order.id())
                        .setStatus(toOrderStatusGrpc(order.status()))
                        .build()
        );
    }

    @Override
    public Mono<OrderCancelResponse> cancel(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp(
                "cancel",
                orderId,
                twoPhaseCommit,
                Set.of(CREATED, INSUFFICIENT, APPROVED),
                CANCELLING,
                order -> toMono(
                        "paymentsClient::cancel",
                        OrdersServiceUtils.newPaymentCancelRequest(order, twoPhaseCommit),
                        paymentsClient::cancel
                ).map(PaymentOuterClass.PaymentCancelResponse::getStatus),
                order -> toMono(
                        "reserveClient::cancel",
                        OrdersServiceUtils.newReserveCancelRequest(order, twoPhaseCommit),
                        reserveClient::cancel
                ).map(ReserveOuterClass.ReserveCancelResponse::getStatus),
                order -> OrderCancelResponse.newBuilder()
                        .setId(order.id())
                        .build()
        );
    }

    private Mono<Tpc.TwoPhaseCommitResponse> commit(
                                                    String operationName,
                                                    String transactionId,
                                                    TwoPhaseCommitServiceStub paymentsClientTcp
    ) {
        return toMono(operationName, OrdersServiceUtils.newCommitRequest(transactionId), paymentsClientTcp::commit)
                .onErrorComplete(e -> OrdersServiceUtils.completeIfNoTransaction(operationName, e, transactionId))
                .doOnError(e -> log.error("error on commit {} {}", operationName, transactionId));
    }

    private Mono<Tpc.TwoPhaseCommitResponse> commitPayment(String transactionId) {
        return commit("paymentsClientTcp::commit", transactionId, paymentsClientTcp);
    }

    private Mono<Tpc.TwoPhaseCommitResponse> commitReserve(String transactionId) {
        return commit("reserveClientTcp::commit", transactionId, reserveClientTcp);
    }

    private Mono<Order> create(Order order, boolean twoPhaseCommit) {
        var orderId = order.id();
        var items = order.items();
        var itemIds = items.stream().map(Order.Item::id).toList();
        var paymentTransactionId = order.paymentTransactionId();
        var paymentRoutine = itemService.getSumCost(itemIds).map(cost -> {
            var paymentRequestBuilder = PaymentOuterClass.PaymentCreateRequest.newBuilder()
                    .setBody(PaymentOuterClass.PaymentCreateRequest.PaymentCreate.newBuilder()
                            .setExternalRef(orderId)
                            .setClientId(order.customerId())
                            .setAmount(cost)
                            .build());
            ofNullable(paymentTransactionId).ifPresent(paymentRequestBuilder::setPreparedTransactionId);
            return paymentRequestBuilder.build();
        }).flatMap(paymentRequest -> {
            return toMono("paymentsClient::create", paymentRequest, paymentsClient::create);
        });

        var reserveTransactionId = order.reserveTransactionId();

        var reserveRequestBuilder = ReserveOuterClass.ReserveCreateRequest.newBuilder()
                .setBody(ReserveOuterClass.ReserveCreateRequest.Reserve.newBuilder()
                        .setExternalRef(orderId)
                        .addAllItems(items.stream()
                                .map(OrdersServiceUtils::toCreateReserveItem)
                                .toList())
                        .build());
        ofNullable(reserveTransactionId).ifPresent(reserveRequestBuilder::setPreparedTransactionId);

        var reserveRoutine = toMono("reserveClient::create", reserveRequestBuilder.build(), reserveClient::create);

        return paymentRoutine.zipWith(reserveRoutine).flatMap(responses -> {
            var paymentResponse = responses.getT1();
            var reserveResponse = responses.getT2();
            var paymentId = paymentResponse.getId();
            var reserveId = reserveResponse.getId();
            return saveAllAndCommit(
                    twoPhaseCommit,
                    order.toBuilder()
                            .status(CREATED)
                            .paymentId(paymentId)
                            .reserveId(reserveId)
                            .build(),
                    paymentTransactionId,
                    reserveTransactionId
            );
        });
    }

    @Override
    public Mono<OrderCreateResponse> create(OrderCreateRequest.OrderCreate createRequest, boolean twoPhaseCommit) {
        return fromSupplier(UUID::randomUUID).map(OrdersServiceUtils::string).flatMap(orderId -> {
            var itemsList = createRequest.getItemsList();

            var orderBuilder = Order.builder()
                    .id(orderId)
                    .status(CREATING)
                    .customerId(createRequest.getCustomerId())
                    .delivery(createRequest.hasDelivery() ? toDelivery(createRequest.getDelivery()) : null)
                    .items(itemsList.stream().map(OrdersServiceUtils::toItem).toList());

            if (twoPhaseCommit) {
                orderBuilder
                        .paymentTransactionId(UUID.randomUUID().toString())
                        .reserveTransactionId(UUID.randomUUID().toString());
            }

            return orderStorage.save(orderBuilder.build()).flatMap(order -> {
                return create(order, twoPhaseCommit);
            });
        }).map(OrdersServiceUtils::toOrderCreateResponse);
    }

    private <T> Mono<T> distributedCommit(
                                          DSLContext dsl,
                                          String orderId,
                                          String paymentTransactionId,
                                          String reserveTransactionId,
                                          T result
    ) {
        return toMono("reserveClientTcp::commit",
                OrdersServiceUtils.newCommitRequest(reserveTransactionId),
                reserveClientTcp::commit)
                .zipWith(toMono("paymentsClientTcp::commit",
                        OrdersServiceUtils.newCommitRequest(paymentTransactionId),
                        paymentsClientTcp::commit
                ))
                .then(TwoPhaseTransaction.commit(dsl, orderId))
                .thenReturn(result)
                .doOnSuccess(s -> {
                    log.debug("distributed transaction commit for order is successful, orderId [{}]", orderId);
                })
                .doOnError(throwable -> log.error(
                        "distributed transaction commit for order is failed, orderId [{}]",
                        orderId,
                        throwable
                ));
    }

    private <T> Mono<T> distributedRollback(
                                            DSLContext dsl,
                                            String orderId,
                                            String orderTransactionId,
                                            String paymentTransactionId,
                                            String reserveTransactionId,
                                            Throwable result
    ) {
        return remoteRollback(paymentTransactionId, reserveTransactionId)
                .then(OrdersServiceUtils.localRollback(dsl, orderTransactionId, result))
                .then(defer(() -> {
                    // todo need check actuality
                    return Mono.<T>error(result);
                }))
                .switchIfEmpty(error(result))
                .doOnSubscribe(_ -> {
                    log.debug("distributed transaction will on rollback, orderId [{}]", orderId);
                })
                .doOnSuccess(_ -> {
                    log.debug("distributed transaction rollback for order is successful, orderId [{}]", orderId);
                })
                .doOnError(e -> {
                    log.error("distributed transaction rollback for order is failed, orderId [{}]", orderId, e);
                });
    }

    @Override
    public Mono<OrderGetResponse> get(String orderId) {
        return orderStorage.getById(orderId).flatMap(order -> {
            return getItems(order.reserveId()).zipWith(
                    getPaymentStatus(order.paymentId()),
                    (items, paymentStatus) -> {
                        return toOrderGrpc(order, paymentStatus, items);
                    }
            );
        }).map(order -> OrderGetResponse.newBuilder().setOrder(order).build());
    }

    private Mono<List<Reserve.Item>> getItems(String reserveId) {
        return toMono("reserveClient::get",
                ReserveOuterClass.ReserveGetRequest.newBuilder()
                        .setId(reserveId)
                        .build(),
                reserveClient::get
        ).map(reserveGetResponse -> {
            return reserveGetResponse.getReserve().getItemsList();
        });
    }

    private Mono<Payment.Status> getPaymentStatus(String paymentId) {
        return toMono("paymentsClient::get",
                PaymentOuterClass.PaymentGetRequest.newBuilder()
                        .setId(paymentId)
                        .build(),
                paymentsClient::get
        ).map(response -> {
            return response.getPayment().getStatus();
        });
    }

    @Override
    public Mono<OrderListResponse> list() {
        return orderStorage.findAll().defaultIfEmpty(List.of()).map(orders -> {
            return orders.stream()
                    .map(order -> toOrderGrpc(order, null, null))
                    .toList();
        }).map(orders -> {
            return OrderListResponse.newBuilder()
                    .addAllOrders(orders)
                    .build();
        });
    }

    @Override
    public Mono<OrderReleaseResponse> release(String orderId, boolean twoPhaseCommit) {
        return updateOrderOp(
                "release",
                orderId,
                twoPhaseCommit,
                Set.of(APPROVED),
                RELEASING,
                order -> toMono(
                        "paymentsClient::pay",
                        OrdersServiceUtils.newPaymentPayRequest(order, twoPhaseCommit),
                        paymentsClient::pay
                ).map(r -> PAID),
                order -> toMono(
                        "reserveClient::release",
                        OrdersServiceUtils.newReserveReleaseRequest(order, twoPhaseCommit),
                        reserveClient::release
                ).map(r -> Reserve.Status.RELEASED),
                order -> OrderReleaseResponse.newBuilder()
                        .setId(order.id())
                        .setStatus(toOrderStatusGrpc(order.status()))
                        .build()
        );
    }

    protected Mono<Void> remoteRollback(String paymentTransactionId, String reserveTransactionId) {
        return toMono("reserveClientTcp::rollback",
                newRollbackRequest(reserveTransactionId),
                reserveClientTcp::rollback)
                .zipWith(toMono("paymentsClientTcp::rollback",
                        newRollbackRequest(paymentTransactionId),
                        paymentsClientTcp::rollback))
                .onErrorComplete(e -> {
                    log.warn("remote rollback error", e);
                    return true;
                })
                .then();
    }

    @Override
    public Mono<OrderResumeResponse> resume(String orderId, boolean twoPhaseCommit) {
        return orderStorage.getById(orderId).flatMap(order -> {
            var status = order.status();
            return switch (status) {
                case CREATED, APPROVED, RELEASED, CANCELLED -> error(newStatusException(status.getCode(),
                        "already committed status"));
                case CREATING -> create(order, twoPhaseCommit).map(o -> OrdersServiceUtils.newOrderResumeResponse(
                        o.id(),
                        toOrderStatusGrpc(o.status())));
                case INSUFFICIENT, APPROVING -> approve(order.id(), twoPhaseCommit).map(o -> OrdersServiceUtils
                        .newOrderResumeResponse(
                                o.getId(),
                                o.getStatus()));
                case RELEASING -> release(order.id(), twoPhaseCommit).map(o -> OrdersServiceUtils
                        .newOrderResumeResponse(
                                o.getId(),
                                o.getStatus()));
                case CANCELLING -> cancel(order.id(), twoPhaseCommit).map(o -> OrdersServiceUtils
                        .newOrderResumeResponse(
                                o.getId(),
                                o.getStatus()));
            };
        });
    }

    protected Mono<Order> saveAllAndCommit(
                                           boolean twoPhaseCommit,
                                           Order order,
                                           String paymentTransactionId,
                                           String reserveTransactionId
    ) {
        var orderId = order.id();
        var orderTransactionId = order.id();
        return jooq.inTransaction(dsl -> {
            var save = orderStorage.save(order);
            // run distributed transaction
            return (twoPhaseCommit ? prepare(dsl, orderTransactionId, save) : save).onErrorResume(throwable -> {
                // rollback distributed transaction on error
                log.error("error on transactional operation with orderId [{}]", orderId, throwable);
                return !twoPhaseCommit
                        ? error(throwable)
                        : distributedRollback(
                                dsl,
                                orderId,
                                orderTransactionId,
                                paymentTransactionId,
                                reserveTransactionId,
                                throwable
                        );
            }).flatMap(savedOrder -> {
                // commit distributed transaction if no errors
                return !twoPhaseCommit
                        ? just(savedOrder)
                        : distributedCommit(
                                dsl,
                                orderId,
                                paymentTransactionId,
                                reserveTransactionId,
                                savedOrder
                        ).doOnError(throwable -> {
                            log.error("error on commit distributed transaction [{}]", orderId, throwable);
                        });
            });
        });
    }

    protected <T, PI, PO, RI, RO> Mono<T> updateOrderOp(
                                                        String opName,
                                                        String orderId,
                                                        boolean twoPhaseCommit,
                                                        Set<Order.Status> expectedFinal,
                                                        Order.Status intermediateStatus,
                                                        Function<Order, Mono<Payment.Status>> paymentOp,
                                                        Function<Order, Mono<Reserve.Status>> reserveOp,
                                                        Function<Order, T> responseBuilder
    ) {
        return orderStorage.getById(orderId).flatMap(order -> {
            return checkStatus(opName, order.status(), expectedFinal, intermediateStatus).then(defer(() -> {
                Mono<Void> commit;
                var uncommitedOrderState = order.status() == intermediateStatus;
                if (!(uncommitedOrderState && twoPhaseCommit)) {
                    commit = empty();
                } else {
                    var paymentTransactionId = requireNonNull(order.paymentTransactionId(), "paymentTransactionId");
                    var reserveTransactionId = requireNonNull(order.reserveTransactionId(), "reserveTransactionId");
                    commit = commitPayment(paymentTransactionId).zipWith(commitReserve(reserveTransactionId))
                            .then(localPreparedTransactions.commit(order.id())
                                    .onErrorComplete(e -> {
                                        var noTransaction = e instanceof NotFoundException;
                                        if (noTransaction) {
                                            OrdersServiceUtils.logNoTransaction("order", order.id());
                                        }
                                        return noTransaction;
                                    })
                                    .doOnError(e -> {
                                        log.error("order commit error {}", order.id(), e);
                                    }))
                            .then();
                }
                return commit.then(defer(() -> {
                    return order.status() == intermediateStatus
                            ? just(order)
                            : orderStorage.save(order.toBuilder().status(intermediateStatus).build());
                }));
            }).then(defer(() -> {
                var paymentTransactionId = order.paymentTransactionId();
                var reserveTransactionId = order.reserveTransactionId();
                return paymentOp.apply(order)
                        .onErrorResume(OrdersServiceUtils.statusError(Payment.Status::valueOf))
                        .zipWith(
                                reserveOp.apply(order)
                                        .onErrorResume(OrdersServiceUtils.statusError(Reserve.Status::valueOf)),
                                (paymentStatus, reserveStatus) -> {
                                    log.debug("payment op '{}' result [{}] ", opName, paymentStatus);
                                    log.debug("reserve op '{}' result [{}] ", opName, reserveStatus);
                                    log.info("order {} [{}]", opName, orderId);
                                    return getOrderStatus(paymentStatus, reserveStatus);
                                }
                        )
                        .onErrorResume(e -> {
                            return twoPhaseCommit
                                    ? remoteRollback(
                                            paymentTransactionId,
                                            reserveTransactionId
                                    ).then(error(e))
                                    : error(e);
                        })
                        .flatMap(status -> {
                            if (status == null) {
                                log.info(
                                        "order status not changed: order [{}], status [{}]",
                                        order.id(),
                                        order.status()
                                );
                                return just(order);
                            } else {
                                log.info(
                                        "order status has been changed: order [{}], status [{}]",
                                        order.id(),
                                        order.status()
                                );
                                var orderWithNewStatus = OrdersServiceUtils.orderWithStatus(
                                        order,
                                        status
                                );
                                if (status == INSUFFICIENT) {
                                    log.info("abort op '{}' on insufficient status of orderId [{}]", opName, orderId);
                                    return orderStorage.save(orderWithNewStatus).flatMap(savedOrder -> {
                                        return twoPhaseCommit
                                                ? remoteRollback(paymentTransactionId, reserveTransactionId)
                                                        .thenReturn(savedOrder)
                                                : just(savedOrder);
                                    });
                                } else {
                                    return saveAllAndCommit(
                                            twoPhaseCommit,
                                            orderWithNewStatus,
                                            paymentTransactionId,
                                            reserveTransactionId
                                    );
                                }
                            }
                        })
                        .map(responseBuilder);
            })));
        });
    }

}
