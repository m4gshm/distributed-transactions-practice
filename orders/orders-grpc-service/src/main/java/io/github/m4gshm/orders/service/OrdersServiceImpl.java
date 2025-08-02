package io.github.m4gshm.orders.service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders.*;
import orders.v1.OrdersServiceGrpc.OrdersServiceImplBase;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass;
import payment.v1.PaymentOuterClass.Payment;
import payment.v1.PaymentOuterClass.PaymentCreateRequest;
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;
import warehouse.v1.Warehouse;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceStub;

import java.util.List;
import java.util.UUID;

import static io.github.m4gshm.ExceptionUtils.newStatusException;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.*;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.*;
import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static io.grpc.Status.FAILED_PRECONDITION;
import static java.util.function.Function.identity;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrdersServiceImpl extends OrdersServiceImplBase {
    GrpcReactive grpc;
    OrderStorage orderRepository;
    ReserveServiceStub reserveClient;
    TwoPhaseCommitServiceStub reserveClientTcp;
    PaymentServiceStub paymentsClient;
    TwoPhaseCommitServiceStub paymentsClientTcp;
    WarehouseItemServiceStub warehouseClient;
    Jooq jooq;

    public static Mono<PaymentOuterClass.PaymentCreateResponse> log(String name, Throwable e) {
        log.error("error on {}", name, e);
        return Mono.error(e);
    }

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        grpc.subscribe(responseObserver, fromSupplier(UUID::randomUUID).map(OrdersServiceUtils::string).flatMap(orderId -> {
            var twoPhaseCommit = request.getTwoPhaseCommit();
            var body = request.getBody();
            var itemsList = body.getItemsList();
            var items = itemsList.stream().map(OrdersServiceUtils::toItem).toList();

            var itemIds = itemsList.stream().map(OrderCreateRequest.OrderBody.Item::getId).distinct().toList();

            var costRoutine = fromIterable(itemIds).flatMap(itemId -> {
                return toMono(Warehouse.GetItemCostRequest.newBuilder()
                        .setId(itemId)
                        .build(), warehouseClient::getItemCost);
            }).map(Warehouse.GetItemCostResponse::getCost).reduce(0.0, Double::sum);

            var paymentRoutine = costRoutine.map(cost -> {
                return PaymentCreateRequest.newBuilder()
                        .setTwoPhaseCommit(twoPhaseCommit)
                        .setBody(Payment.newBuilder()
                                .setExternalRef(orderId)
                                .setClientId(body.getCustomerId())
                                .setAmount(cost)
                                .build())
                        .setTwoPhaseCommit(twoPhaseCommit)
                        .build();
            }).flatMap(paymentRequest -> {
                return toMono(paymentRequest, paymentsClient::create).onErrorResume(e -> {
                    return log("payment::create", e);
                });
            });

            var reserveRequest = ReserveOuterClass.ReserveCreateRequest.newBuilder()
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .setBody(ReserveOuterClass.ReserveCreateRequest.Reserve.newBuilder()
                            .setExternalRef(orderId)
                            .addAllItems(items.stream().map(OrdersServiceUtils::toCreateReserveItem).toList())
                            .build()
                    ).build();
            var reserveRoutine = toMono(reserveRequest, reserveClient::create);

            return paymentRoutine.zipWith(reserveRoutine).flatMap(responses -> {
                var paymentResponse = responses.getT1();
                var reserveResponse = responses.getT2();
                var paymentId = paymentResponse.getId();
                var reserveId = reserveResponse.getId();
                var order = Order.builder()
                        .id(string(orderId))
                        .status(Order.Status.created)
                        .paymentId(paymentId)
                        .reserveId(reserveId)
                        .customerId(body.getCustomerId())
                        .delivery(toDelivery(body.getDelivery()))
                        .items(items)
                        .build();
                return saveAndCommit(twoPhaseCommit, order, paymentId, reserveId);
            });
        }).map(order -> {
            return toOrderCreateResponse(order);
        }));
    }

    @Override
    public void approve(OrderApproveRequest request, StreamObserver<OrderApproveResponse> responseObserver) {
        var orderId = request.getId();
        grpc.subscribe(responseObserver, orderRepository.getById(orderId).flatMap(order -> {

            if (order.status() == Order.Status.approved) {
                return error(newStatusException(FAILED_PRECONDITION, "already approved"));
            }
            var twoPhaseCommit = request.getTwoPhaseCommit();
            var paymentId = order.paymentId();
            var reserveId = order.reserveId();
            var paymentApproveRequest = PaymentOuterClass.PaymentApproveRequest.newBuilder()
                    .setId(paymentId)
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .build();
            var reserveApproveRequest = ReserveOuterClass.ReserveApproveRequest.newBuilder()
                    .setId(reserveId)
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .build();
            return toMono(paymentApproveRequest, paymentsClient::approve
            ).zipWith(toMono(reserveApproveRequest, reserveClient::approve), (payment, reserve) -> {
                var paymentStatus = payment.getStatus();
                var reserveStatus = reserve.getStatus();
                log.info("approve statuses: payment [{}], reserve [{}]", paymentStatus, reserveStatus);

                var newOrderStatus = getOrderStatus(paymentStatus, reserveStatus);
                log.trace("new order status [{}]", newOrderStatus);

                var orderOnSave = order.toBuilder()
                        .items(populateItemStatus(order, reserve))
                        .status(newOrderStatus)
                        .paymentInsufficientValue(payment.getInsufficientAmount())
                        .build();

                return saveAndCommit(twoPhaseCommit, orderOnSave, paymentId, reserveId);
            }).flatMap(identity());
        }).map(order -> OrderApproveResponse.newBuilder()
                .setId(order.id())
                .setStatus(toOrderStatus(order.status()))
                .build()));
    }

    private Mono<Order> saveAndCommit(boolean twoPhaseCommit, Order order,
                                      String paymentTransactionId, String reserveTransactionId) {
        return jooq.transactional(dsl -> {
            //run distributed transaction
            return prepare(twoPhaseCommit, dsl, order.id(), orderRepository.save(order)
            ).onErrorResume(throwable -> {
                return handlePreparedError(twoPhaseCommit, dsl, order.id(), paymentTransactionId,
                        reserveTransactionId, throwable);
            }).flatMap(savedOrder -> {
                return handlePreparedSuccess(twoPhaseCommit, dsl, order.id(), paymentTransactionId,
                        reserveTransactionId, savedOrder);
            });
        });
    }

    private <T> Mono<T> handlePreparedSuccess(boolean twoPhaseCommit, DSLContext dsl, String orderId,
                                              String paymentTransactionId, String reserveTransactionId, T result) {
        //commit distributed transaction if no errors
        return !twoPhaseCommit ? just(result) : distributedCommit(dsl, orderId, reserveTransactionId,
                paymentTransactionId, result
        ).doOnError(throwable -> {
            log.error("error on commit distributed transaction [{}]", orderId, throwable);
        });
    }

    private <T> Mono<T> handlePreparedError(boolean twoPhaseCommit, DSLContext dsl, String orderId,
                                            String paymentTransactionId, String reserveTransactionId, Throwable result) {
        //rollback distributed transaction on error
        log.error("error on transactional operation with orderId [{}]", orderId, result);
        return !twoPhaseCommit ? error(result) : distributedRollback(dsl, orderId, reserveTransactionId,
                paymentTransactionId, result);
    }

    private <T> Mono<T> distributedCommit(DSLContext dsl, String orderId, String reserveTransactionId,
                                          String paymentTransactionId, T result) {
        return toMono(newCommitRequest(reserveTransactionId), reserveClientTcp::commit)
                .zipWith(toMono(newCommitRequest(paymentTransactionId), paymentsClientTcp::commit))
                .then(commit(dsl, string(orderId)))
                .thenReturn(result)
                .doOnSuccess(_ -> {
                    log.debug("distributed transaction commit for order is successful, orderId [{}]", orderId);
                }).doOnError(throwable -> {
                    log.error("distributed transaction commit for order is failed, orderId [{}]", orderId, throwable);
                });
    }

    private <T> Mono<T> distributedRollback(DSLContext dsl, String orderId, String reserveTransactionId,
                                            String paymentTransactionId, Throwable result) {
        var localRollback = result instanceof PrepareTransactionException
                ? rollback(dsl, string(orderId))
                : Mono.<Integer>empty().doOnSubscribe(_ -> {
            log.debug("no local prepared transaction for rollback");
        });
        return toMono(newRollbackRequest(reserveTransactionId), reserveClientTcp::rollback)
                .zipWith(toMono(newRollbackRequest(paymentTransactionId), paymentsClientTcp::rollback))
                .then(localRollback)
                .then(defer(() -> {
                    //todo need check actuality
                    return Mono.<T>error(result);
                })).switchIfEmpty(
                        error(result)
                ).doOnSubscribe(_ -> {
                    log.debug("distributed transaction will on rollback, orderId [{}]", orderId);
                }).doOnSuccess(_ -> {
                    log.debug("distributed transaction rollback for order is successful, orderId [{}]", orderId);
                }).doOnError(e -> {
                    log.error("distributed transaction rollback for order is failed, orderId [{}]", orderId, e);
                });
    }

    @Override
    public void get(OrderGetRequest request, StreamObserver<OrderGetResponse> responseObserver) {
        grpc.subscribe(responseObserver, just(request)
                .map(OrderGetRequest::getId)
                .flatMap(id -> orderRepository.findById(id).switchIfEmpty(notFoundById(id)))
                .map(OrdersServiceUtils::toOrder).map(order -> OrderGetResponse.newBuilder()
                        .setOrder(order).build())
        );
    }

    @Override
    public void list(OrderListRequest request, StreamObserver<OrderListResponse> responseObserver) {
        grpc.subscribe(responseObserver, just(request).flatMap(_ -> {
            return orderRepository.findAll().defaultIfEmpty(List.of());
        }).map(orders -> {
            return orders.stream()
                    .map(OrdersServiceUtils::toOrder)
                    .toList();
        }).map(orders -> {
            return OrderListResponse.newBuilder()
                    .addAllOrders(orders)
                    .build();
        }));
    }

    @Override
    public void cancel(OrderCancelRequest request, StreamObserver<OrderCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

}
