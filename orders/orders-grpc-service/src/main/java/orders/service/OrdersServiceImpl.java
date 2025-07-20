package orders.service;

import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.data.model.Order;
import orders.data.storage.OrderStorage;
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
import payments.v1.Payments;
import payments.v1.Payments.NewPaymentRequest;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceStub;
import reactor.core.publisher.Mono;
import reserve.v1.Reserve;
import reserve.v1.Reserve.NewReserveRequest;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.Tpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.UUID;

import static jooq.utils.TwoPhaseTransaction.commit;
import static jooq.utils.TwoPhaseTransaction.rollback;
import static orders.service.OrdersServiceUtils.notFoundById;
import static orders.service.OrdersServiceUtils.string;
import static orders.service.OrdersServiceUtils.toDelivery;
import static orders.service.OrdersServiceUtils.uuid;
import static reactive.GrpcUtils.subscribe;
import static reactive.ReactiveUtils.toMono;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.fromSupplier;
import static reactor.core.publisher.Mono.just;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrdersServiceImpl extends OrdersServiceImplBase {
    DSLContext dsl;
    OrderStorage orderRepository;
    ReserveServiceStub reserveClient;
    TwoPhaseCommitServiceStub reserveClientTcp;
    PaymentsServiceStub paymentsClient;
    TwoPhaseCommitServiceStub paymentsClientTcp;

    private static OrderCreateResponse toOrderCreateResponse(Order order) {
        return OrderCreateResponse.newBuilder().setId(string(order.id())).build();
    }

    private static Tpc.TwoPhaseCommitRequest newCommitRequest(String id) {
        return Tpc.TwoPhaseCommitRequest.newBuilder().setId(string(id)).build();
    }

    private static Tpc.TwoPhaseRollbackRequest newRollbackRequest(String reserveResponse) {
        return Tpc.TwoPhaseRollbackRequest.newBuilder()
                .setId(string(reserveResponse))
                .build();
    }

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> response) {
        subscribe(response, fromSupplier(UUID::randomUUID).map(OrdersServiceUtils::string).flatMap(orderId -> {
            var twoPhaseCommit = request.getTwoPhaseCommit();
            var body = request.getBody();
            var itemsList = body.getItemsList();
            var items = itemsList.stream().map(OrdersServiceUtils::toItem).toList();

            var amount = items.stream().mapToDouble(Order.Item::cost).sum();

            var paymentRequest = NewPaymentRequest.newBuilder()
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .setBody(NewPaymentRequest.PaymentBody.newBuilder()
                            .setExternalRef(orderId)
                            .setAmount(amount)
                            .build())
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .build();
            var reserveRequest = NewReserveRequest.newBuilder()
                    .setTwoPhaseCommit(twoPhaseCommit)
                    .setBody(NewReserveRequest.ReserveBody.newBuilder()
                            .setExternalRef(orderId)
                            .addAllItems(items.stream().map(OrdersServiceUtils::toReserveItem).toList())
                            .build()
                    ).build();

            var reserveRoutine = toMono(reserveRequest, reserveClient::create);
            var paymentRoutine = toMono(paymentRequest, paymentsClient::create);

            return paymentRoutine.zipWith(reserveRoutine).flatMap(responses -> {
                var paymentResponse = responses.getT1();
                var reserveResponse = responses.getT2();
                return orderRepository.save(Order.builder()
                        .id(string(orderId))
                        .paymentId(uuid(paymentResponse.getId()))
                        .reserveId(uuid(reserveResponse.getId()))
                        .customerId(uuid(body.getCustomerId()))
                        .delivery(toDelivery(body.getDelivery()))
                        .items(items)
                        .build(), twoPhaseCommit
                ).flatMap(order -> {
                    return !twoPhaseCommit
                            ? just(order)
                            : getDistributedCommit(order, orderId, reserveResponse, paymentResponse);
                }).onErrorResume(throwable -> {
                    return !twoPhaseCommit
                            ? Mono.error(throwable)
                            : getDistributedRollback(throwable, orderId, reserveResponse, paymentResponse);
                });
            });
        }).map(OrdersServiceImpl::toOrderCreateResponse));
    }

    private Mono<Order> getDistributedCommit(Order result, String orderId,
                                             Reserve.NewReserveResponse reserveResponse,
                                             Payments.NewPaymentResponse paymentResponse) {
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

    private Mono<Order> getDistributedRollback(Throwable throwable, String orderId,
                                               Reserve.NewReserveResponse reserveResponse,
                                               Payments.NewPaymentResponse paymentResponse) {
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
