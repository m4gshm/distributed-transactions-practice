package io.github.m4gshm.orders.service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.utils.TwoPhaseTransaction;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders.OrderApproveRequest;
import orders.v1.Orders.OrderApproveResponse;
import orders.v1.Orders.OrderCancelRequest;
import orders.v1.Orders.OrderCancelResponse;
import orders.v1.Orders.OrderCreateRequest;
import orders.v1.Orders.OrderCreateResponse;
import orders.v1.Orders.OrderFindRequest;
import orders.v1.Orders.OrderGetRequest;
import orders.v1.Orders.OrderResponse;
import orders.v1.Orders.OrdersResponse;
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

import java.util.UUID;

import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.commit;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.rollback;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.newCommitRequest;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.newRollbackRequest;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.notFoundById;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.string;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.toDelivery;
import static io.github.m4gshm.orders.service.OrdersServiceUtils.uuid;
import static io.github.m4gshm.reactive.GrpcUtils.subscribe;
import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.fromSupplier;
import static reactor.core.publisher.Mono.just;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrdersServiceImpl extends OrdersServiceImplBase {
    OrderStorage orderRepository;
    ReserveServiceStub reserveClient;
    TwoPhaseCommitServiceStub reserveClientTcp;
    PaymentServiceStub paymentsClient;
    TwoPhaseCommitServiceStub paymentsClientTcp;
    Jooq jooq;

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        subscribe(responseObserver, fromSupplier(UUID::randomUUID).map(OrdersServiceUtils::string).flatMap(orderId -> {
            var twoPhaseCommit = request.getTwoPhaseCommit();
            var body = request.getBody();
            var itemsList = body.getItemsList();
            var items = itemsList.stream().map(OrdersServiceUtils::toItem).toList();

            var amount = items.stream().mapToDouble(Order.Item::cost).sum();

            var paymentRequest = PaymentCreateRequest.newBuilder()
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .setBody(Payment.newBuilder()
                            .setExternalRef(orderId)
                            .setAmount(amount)
                            .build())
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .build();
            var reserveRequest = ReserveOuterClass.ReserveCreateRequest.newBuilder()
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .setBody(ReserveOuterClass.ReserveCreateRequest.Reserve.newBuilder()
                            .setExternalRef(orderId)
                            .addAllItems(items.stream().map(OrdersServiceUtils::toCreateReserveItem).toList())
                            .build()
                    ).build();

            var reserveRoutine = toMono(reserveRequest, reserveClient::create);
            var paymentRoutine = toMono(paymentRequest, paymentsClient::create);

            return paymentRoutine.zipWith(reserveRoutine).flatMap(responses -> {
                var paymentResponse = responses.getT1();
                var reserveResponse = responses.getT2();
                return jooq.transactional(dsl -> TwoPhaseTransaction.prepare(dsl, orderId, orderRepository.save(Order.builder()
                        .id(string(orderId))
                        .paymentId(uuid(paymentResponse.getId()))
                        .reserveId(uuid(reserveResponse.getId()))
                        .customerId(uuid(body.getCustomerId()))
                        .delivery(toDelivery(body.getDelivery()))
                        .items(items)
                        .build()
                )).flatMap(order -> {
                    return !twoPhaseCommit
                            ? just(order)
                            : getDistributedCommit(dsl, order, orderId, reserveResponse, paymentResponse);
                }).onErrorResume(throwable -> {
                    return !twoPhaseCommit
                            ? Mono.error(throwable)
                            : getDistributedRollback(dsl, throwable, orderId, reserveResponse, paymentResponse);
                }));
            });
        }).map(OrdersServiceUtils::toOrderCreateResponse));
    }

    @Override
    public void approve(OrderApproveRequest request, StreamObserver<OrderApproveResponse> responseObserver) {
        subscribe(responseObserver, fromSupplier(() -> {
//            orderRepository.getById(request.getId()).map(order -> {
//                var paymentId = order.paymentId();
//                var reserveId = order.reserveId();
//
//                toMono(PaymentApproveRequest.newBuilder().setId(reserveId).build(), paymentsClient::approve).map(paymentResponse -> {
//                    paymentResponse.getStatus();
//                });
//
//            });


            var r = OrderApproveResponse.newBuilder().build();
            return r;
        }));
    }

    private Mono<Order> getDistributedCommit(DSLContext dsl, Order result, String orderId,
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

    private Mono<Order> getDistributedRollback(DSLContext dsl, Throwable throwable, String orderId,
                                               ReserveCreateResponse reserveResponse,
                                               PaymentCreateResponse paymentResponse) {
        return toMono(newRollbackRequest(reserveResponse.getId()), reserveClientTcp::rollback)
                .zipWith(toMono(newRollbackRequest(paymentResponse.getId()), paymentsClientTcp::rollback))
                .then(rollback(dsl, string(orderId)))
                .then(defer(() -> {
                    //todo need check actuality
                    return Mono.<Order>error(throwable);
                })).switchIfEmpty(
                        Mono.error(throwable)
                ).doOnSubscribe(s -> {
                    log.debug("distributed transaction will on rollback, orderId: {}", orderId);
                }).doOnSuccess(order -> {
                    log.debug("distributed transaction rollback for order is successful, orderId: {}", orderId);
                }).doOnError(e -> {
                    log.debug("distributed transaction rollback for order is failed, orderId: {}", orderId, e);
                });
    }

    @Override
    public void get(OrderGetRequest request, StreamObserver<OrderResponse> response) {
        subscribe(response, just(request)
                .map(OrderGetRequest::getId)
                .flatMap(id -> orderRepository.findById(id).switchIfEmpty(notFoundById(id)))
                .map(OrdersServiceUtils::toOrderResponse));
    }

    @Override
    public void cancel(OrderCancelRequest request, StreamObserver<OrderCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void search(OrderFindRequest request, StreamObserver<OrdersResponse> response) {
        subscribe(response, orderRepository.findAll().map(OrdersServiceUtils::toOrdersResponse));
    }
}
