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
import payment.v1.PaymentOuterClass.Payment;
import payment.v1.PaymentOuterClass.PaymentCreateRequest;
import payment.v1.PaymentOuterClass.PaymentCreateResponse;
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import reactor.core.publisher.Mono;
import reserve.v1.ReserveOuterClass;
import reserve.v1.ReserveOuterClass.ReserveCreateResponse;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;
import warehouse.v1.Warehouse;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceStub;

import java.util.List;
import java.util.UUID;

import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.*;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.*;
import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static lombok.AccessLevel.PRIVATE;
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

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        grpc.subscribe(responseObserver, fromSupplier(UUID::randomUUID).map(OrdersServiceUtils::string).flatMap(orderId -> {
            var twoPhaseCommit = request.getTwoPhaseCommit();
            var body = request.getBody();
            var itemsList = body.getItemsList();
            var items = itemsList.stream().map(OrdersServiceUtils::toItem).toList();

            var itemIds = itemsList.stream().map(OrderCreateRequest.OrderBody.Item::getId).distinct().toList();

            var costRoutine = toMono(Warehouse.GetItemCostRequest.newBuilder()
                    .addAllItemIds(itemIds)
                    .build(), warehouseClient::getItemCost).map(Warehouse.GetItemCostResponse::getSumCost);

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
                return toMono(paymentRequest, paymentsClient::create);
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
                return jooq.transactional(dsl -> {
                    //run distributed transaction
                    return prepare(twoPhaseCommit, dsl, orderId, orderRepository.save(Order.builder()
                            .id(string(orderId))
                            .status(Order.Status.created)
                            .paymentId(uuid(paymentResponse.getId()))
                            .reserveId(uuid(reserveResponse.getId()))
                            .customerId(uuid(body.getCustomerId()))
                            .delivery(toDelivery(body.getDelivery()))
                            .items(items)
                            .build()
                    )).onErrorResume(throwable -> {
                        //rollback distributed transaction on error
                        log.error("error on create order {}", orderId, throwable);
                        return !twoPhaseCommit
                                ? error(throwable)
                                : distributedRollback(dsl, throwable, orderId, reserveResponse, paymentResponse);
                    }).flatMap(order -> {
                        //commit distributed transaction if no errors
                        return !twoPhaseCommit
                                ? just(order)
                                : distributedCommit(dsl, order, orderId, reserveResponse, paymentResponse)
                                .doOnError(throwable -> {
                                    log.error("error on rollback distributed transaction {}", orderId, throwable);
                                });
                    });
                });
            });
        }).map(order -> toOrderCreateResponse(order)));
    }

    @Override
    public void approve(OrderApproveRequest request, StreamObserver<OrderApproveResponse> responseObserver) {
        grpc.subscribe(responseObserver, orderRepository.getById(request.getId()).flatMap(order -> {
            var paymentId = order.paymentId();
            var reserveId = order.reserveId();
            return toMono(newPaymentApproveRequest(paymentId), paymentsClient::approve).zipWith(
                    toMono(newReserveApproveRequest(reserveId), reserveClient::approve),
                    (payment, reserve) -> {
                        var paymentStatus = payment.getStatus();
                        var reserveStatus = reserve.getStatus();
                        log.info("approve statuses: payment [{}], reserve [{}]", paymentStatus, reserveStatus);
                        var newOrderStatus = getOrderStatus(paymentStatus, reserveStatus);
                        log.trace("new order status [{}]", newOrderStatus);

                        return order.toBuilder()
                                .items(populateItemStatus(order, reserve))
                                .status(newOrderStatus)
                                .build();
                    });
        }).flatMap(orderRepository::save).map(order -> {
            return OrderApproveResponse.newBuilder()
                    .setId(order.id())
                    .build();
        }));
    }

    private Mono<Order> distributedCommit(DSLContext dsl, Order result, String orderId,
                                          ReserveCreateResponse reserveResponse,
                                          PaymentCreateResponse paymentResponse) {
        return toMono(newCommitRequest(reserveResponse.getId()), reserveClientTcp::commit)
                .zipWith(toMono(newCommitRequest(paymentResponse.getId()), paymentsClientTcp::commit))
                .then(commit(dsl, string(orderId)))
                .thenReturn(result)
                .doOnSuccess(order -> {
                    log.debug("distributed transaction commit for order is successful, orderId: {}", orderId);
                }).doOnError(throwable -> {
                    log.debug("distributed transaction commit for order is failed, orderId: {}", orderId, throwable);
                });
    }

    private Mono<Order> distributedRollback(DSLContext dsl, Throwable throwable, String orderId,
                                            ReserveCreateResponse reserveResponse,
                                            PaymentCreateResponse paymentResponse) {
        return toMono(newRollbackRequest(reserveResponse.getId()), reserveClientTcp::rollback)
                .zipWith(toMono(newRollbackRequest(paymentResponse.getId()), paymentsClientTcp::rollback))
                .then(rollback(dsl, string(orderId)))
                .then(defer(() -> {
                    //todo need check actuality
                    return Mono.<Order>error(throwable);
                })).switchIfEmpty(
                        error(throwable)
                ).doOnSubscribe(s -> {
                    log.debug("distributed transaction will on rollback, orderId: {}", orderId);
                }).doOnSuccess(order -> {
                    log.debug("distributed transaction rollback for order is successful, orderId: {}", orderId);
                }).doOnError(e -> {
                    log.debug("distributed transaction rollback for order is failed, orderId: {}", orderId, e);
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
        grpc.subscribe(responseObserver, just(request)
                .flatMap(r -> {
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
