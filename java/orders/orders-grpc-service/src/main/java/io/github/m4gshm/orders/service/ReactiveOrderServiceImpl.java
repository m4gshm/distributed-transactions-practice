package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.ReactiveOrderStorage;
import io.github.m4gshm.postgres.prepared.transaction.ReactivePreparedTransactionService;
import io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransactionUtils.PrepareTransactionException;
import io.github.m4gshm.storage.NotFoundException;
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
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import payment.v1.PaymentServiceOuterClass.PaymentApproveResponse;
import payment.v1.PaymentServiceOuterClass.PaymentCancelResponse;
import payment.v1.PaymentServiceOuterClass.PaymentCreateRequest;
import payment.v1.PaymentServiceOuterClass.PaymentGetRequest;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass.Reserve;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceOuterClass;
import reserve.v1.ReserveServiceOuterClass.ReserveApproveResponse;
import reserve.v1.ReserveServiceOuterClass.ReserveCreateRequest;
import reserve.v1.ReserveServiceOuterClass.ReserveGetRequest;
import tpc.v1.TpcService.TwoPhaseCommitResponse;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.github.m4gshm.ExceptionUtils.newStatusException;
import static io.github.m4gshm.ReactiveExceptionUtils.checkStatus;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.APPROVED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.APPROVING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CANCELLING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CREATED;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.CREATING;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.INSUFFICIENT;
import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.RELEASING;
import static io.github.m4gshm.orders.service.OrderServiceReactiveUtils.statusError;
import static io.github.m4gshm.orders.service.OrderServiceUtils.getOrderStatus;
import static io.github.m4gshm.orders.service.OrderServiceUtils.logNoTransaction;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newPaymentCancelRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newReserveCancelRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.newRollbackRequest;
import static io.github.m4gshm.orders.service.OrderServiceUtils.toDelivery;
import static io.github.m4gshm.orders.service.OrderServiceUtils.toOrderGrpc;
import static io.github.m4gshm.orders.service.OrderServiceUtils.toOrderStatusGrpc;
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

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PROTECTED)
public class ReactiveOrderServiceImpl implements ReactiveOrderService {
    ReactiveOrderStorage orderStorage;
    ReactivePreparedTransactionService preparedTransactionService;

    ReserveServiceGrpc.ReserveServiceStub reserveClient;

    TwoPhaseCommitServiceStub reserveClientTcp;
    PaymentServiceStub paymentsClient;
    TwoPhaseCommitServiceStub paymentsClientTcp;
    ReactiveItemService reactiveItemService;

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
                        OrderServiceUtils.newPaymentApproveRequest(order, twoPhaseCommit),
                        paymentsClient::approve
                ).map(PaymentApproveResponse::getStatus),
                order -> toMono(
                        "reserveClient::approve",
                        OrderServiceUtils.newReserveApproveRequest(order, twoPhaseCommit),
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
                        newPaymentCancelRequest(order, twoPhaseCommit),
                        paymentsClient::cancel
                ).map(PaymentCancelResponse::getStatus),
                order -> toMono(
                        "reserveClient::cancel",
                        newReserveCancelRequest(order, twoPhaseCommit),
                        reserveClient::cancel
                ).map(ReserveServiceOuterClass.ReserveCancelResponse::getStatus),
                order -> OrderServiceOuterClass.OrderCancelResponse.newBuilder()
                        .setId(order.id())
                        .build()
        );
    }

    private Mono<TwoPhaseCommitResponse> commit(
                                                String operationName,
                                                String transactionId,
                                                TwoPhaseCommitServiceStub paymentsClientTcp
    ) {
        return toMono(operationName,
                OrderServiceUtils.newCommitRequest(transactionId),
                paymentsClientTcp::commit)
                .onErrorComplete(e -> OrderServiceUtils.completeIfNoTransaction(operationName, e, transactionId))
                .doOnError(e -> log.error("error on commit {} {}", operationName, transactionId));
    }

    private Mono<TwoPhaseCommitResponse> commitPayment(String transactionId) {
        return commit("paymentsClientTcp::commit", transactionId, paymentsClientTcp);
    }

    private Mono<TwoPhaseCommitResponse> commitReserve(String transactionId) {
        return commit("reserveClientTcp::commit", transactionId, reserveClientTcp);
    }

    private Mono<Order> create(Order order, boolean twoPhaseCommit) {
        var orderId = order.id();
        var items = order.items();
        var paymentTransactionId = order.paymentTransactionId();
        var paymentRoutine = reactiveItemService.getSumCost(items).map(cost -> {
            var paymentRequestBuilder = PaymentCreateRequest.newBuilder()
                    .setBody(PaymentCreateRequest.PaymentCreate.newBuilder()
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

        var reserveRequestBuilder = ReserveCreateRequest.newBuilder()
                .setBody(ReserveCreateRequest.Reserve.newBuilder()
                        .setExternalRef(orderId)
                        .addAllItems(items.stream()
                                .map(OrderServiceUtils::toCreateReserveItem)
                                .toList())
                        .build());
        ofNullable(reserveTransactionId).ifPresent(reserveRequestBuilder::setPreparedTransactionId);

        var reserveRoutine = toMono("reserveClient::create",
                reserveRequestBuilder.build(),
                reserveClient::create);

        return paymentRoutine.zipWith(reserveRoutine).flatMap(responses -> {
            var paymentResponse = responses.getT1();
            var reserveResponse = responses.getT2();
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
        });
    }

    @Override
    public Mono<OrderCreateResponse> create(OrderCreateRequest.OrderCreate createRequest, boolean twoPhaseCommit) {
        return fromSupplier(UUID::randomUUID).map(OrderServiceUtils::string).flatMap(orderId -> {
            var itemsList = createRequest.getItemsList();

            var orderBuilder = Order.builder()
                    .id(orderId)
                    .status(CREATING)
                    .customerId(createRequest.getCustomerId())
                    .delivery(createRequest.hasDelivery()
                            ? toDelivery(createRequest.getDelivery())
                            : null)
                    .items(itemsList.stream().map(OrderServiceUtils::toItem).toList());

            if (twoPhaseCommit) {
                orderBuilder
                        .paymentTransactionId(UUID.randomUUID().toString())
                        .reserveTransactionId(UUID.randomUUID().toString());
            }

            return orderStorage.save(orderBuilder.build()).flatMap(order -> {
                return create(order, twoPhaseCommit);
            });
        }).map(OrderServiceUtils::toOrderCreateResponse);
    }

    private <T> Mono<T> distributedCommit(
                                          String orderId,
                                          String paymentTransactionId,
                                          String reserveTransactionId,
                                          T result
    ) {
        return toMono("reserveClientTcp::commit",
                OrderServiceUtils.newCommitRequest(reserveTransactionId),
                reserveClientTcp::commit)
                .zipWith(toMono("paymentsClientTcp::commit",
                        OrderServiceUtils.newCommitRequest(paymentTransactionId),
                        paymentsClientTcp::commit
                ))
                .then(preparedTransactionService.commit(orderId))
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
                                            String orderId,
                                            String orderTransactionId,
                                            String paymentTransactionId,
                                            String reserveTransactionId,
                                            Throwable result
    ) {
        return remoteRollback(paymentTransactionId, reserveTransactionId)
                .then(result instanceof PrepareTransactionException
                        ? preparedTransactionService.rollback(orderTransactionId)
                        : Mono.<Void>empty().doOnSubscribe(_1 -> {
                            log.debug("no local prepared transaction for rollback");
                        }))
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
        }).map(order -> OrderGetResponse.newBuilder().setOrder(order).build()).name("get");
    }

    private Mono<List<Reserve.Item>> getItems(String reserveId) {
        return toMono("reserveClient::get",
                ReserveGetRequest.newBuilder()
                        .setId(reserveId)
                        .build(),
                reserveClient::get
        ).map(reserveGetResponse -> {
            return reserveGetResponse.getReserve().getItemsList();
        });
    }

    private Mono<Payment.Status> getPaymentStatus(String paymentId) {
        return toMono("paymentsClient::get",
                PaymentGetRequest.newBuilder()
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
                        OrderServiceUtils.newPaymentPayRequest(order, twoPhaseCommit),
                        paymentsClient::pay
                ).map(r -> PAID),
                order -> toMono(
                        "reserveClient::release",
                        OrderServiceUtils.newReserveReleaseRequest(order, twoPhaseCommit),
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
                case CREATED, APPROVED, RELEASED, CANCELLED -> throw newStatusException(status.getLiteral(),
                        "already committed status");
                case CREATING -> create(order, twoPhaseCommit).map(o -> OrderServiceUtils.newOrderResumeResponse(
                        o.id(),
                        toOrderStatusGrpc(o.status())));
                case INSUFFICIENT, APPROVING -> approve(order.id(), twoPhaseCommit).map(o -> OrderServiceUtils
                        .newOrderResumeResponse(
                                o.getId(),
                                o.getStatus()));
                case RELEASING -> release(order.id(), twoPhaseCommit).map(o -> OrderServiceUtils
                        .newOrderResumeResponse(
                                o.getId(),
                                o.getStatus()));
                case CANCELLING -> cancel(order.id(), twoPhaseCommit).map(o -> OrderServiceUtils
                        .newOrderResumeResponse(
                                o.getId(),
                                o.getStatus()));
            };
        });
    }

    protected Mono<Order> updateOrderAndCommit(boolean twoPhaseCommit,
                                               Order order,
                                               String paymentTransactionId,
                                               String reserveTransactionId) {
        var orderId = order.id();
        var save = orderStorage.saveOrderOnly(order);
        // run distributed transaction
        return (twoPhaseCommit ? preparedTransactionService.prepare(orderId, save) : save)
                .onErrorResume(
                        throwable -> {
                            // rollback distributed transaction on error
                            log.error("error on transactional operation with orderId [{}]", orderId, throwable);
                            return !twoPhaseCommit
                                    ? error(throwable)
                                    : distributedRollback(
                                            orderId,
                                            orderId,
                                            paymentTransactionId,
                                            reserveTransactionId,
                                            throwable
                                    );
                        })
                .flatMap(savedOrder -> {
                    // commit distributed transaction if no errors
                    return !twoPhaseCommit
                            ? just(savedOrder)
                            : distributedCommit(
                                    orderId,
                                    paymentTransactionId,
                                    reserveTransactionId,
                                    savedOrder
                            ).doOnError(throwable -> {
                                log.error("error on commit distributed transaction [{}]", orderId, throwable);
                            });
                });
    }

    protected <T, PI, PO, RI, RO> Mono<T> updateOrderOp(
                                                        String opName,
                                                        String orderId,
                                                        boolean twoPhaseCommit,
                                                        Set<OrderStatus> expectedFinal,
                                                        OrderStatus intermediateStatus,
                                                        Function<Order, Mono<Payment.Status>> paymentOp,
                                                        Function<Order, Mono<Reserve.Status>> reserveOp,
                                                        Function<Order, T> responseBuilder
    ) {
        return orderStorage.getById(orderId).flatMap(order -> {
            return checkStatus(opName, "order", orderId, order.status(), expectedFinal, intermediateStatus).then(defer(
                    () -> {
                        Mono<Void> commit;
                        var uncommitedOrderState = order.status() == intermediateStatus;
                        if (!(uncommitedOrderState && twoPhaseCommit)) {
                            commit = empty();
                        } else {
                            var paymentTransactionId = requireNonNull(order.paymentTransactionId(),
                                    "paymentTransactionId");
                            var reserveTransactionId = requireNonNull(order.reserveTransactionId(),
                                    "reserveTransactionId");
                            commit = commitPayment(paymentTransactionId).zipWith(commitReserve(reserveTransactionId))
                                    .then(preparedTransactionService.commit(order.id())
                                            .onErrorComplete(e -> {
                                                var noTransaction = e instanceof NotFoundException;
                                                if (noTransaction) {
                                                    logNoTransaction("order", order.id());
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
                        // payment and reserve ops
                        var paymentTransactionId = order.paymentTransactionId();
                        var reserveTransactionId = order.reserveTransactionId();
                        return paymentOp.apply(order)
                                .onErrorResume(statusError(Payment.Status::valueOf))
                                .zipWith(
                                        reserveOp.apply(order)
                                                .onErrorResume(statusError(Reserve.Status::valueOf)),
                                        (paymentStatus, reserveStatus) -> {
                                            log.trace("payment op '{}' result [{}] ", opName, paymentStatus);
                                            log.trace("reserve op '{}' result [{}] ", opName, reserveStatus);
                                            log.debug("order {} [{}]", opName, orderId);
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
                                            log.info("abort op '{}' on insufficient status of orderId [{}]",
                                                    opName,
                                                    orderId);
                                            return orderStorage.save(orderWithNewStatus).flatMap(savedOrder -> {
                                                return twoPhaseCommit
                                                        ? remoteRollback(paymentTransactionId, reserveTransactionId)
                                                                .thenReturn(savedOrder)
                                                        : just(savedOrder);
                                            });
                                        } else {
                                            return updateOrderAndCommit(
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
        }).doOnSuccess(t -> {
            log.debug("{}, orderId [{}]", opName, orderId);
        });
    }
}
