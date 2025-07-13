package orders.service;

import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.data.model.OrderEntity;
import orders.data.repository.OrderRepository;
import orders.v1.Orders.*;
import orders.v1.OrdersServiceGrpc.OrdersServiceImplBase;
import payments.v1.Payments.NewPaymentRequest;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceStub;
import reactor.core.publisher.Mono;
import reserve.v1.Reserve.NewReserveRequest;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;

import java.util.List;
import java.util.UUID;

import static io.grpc.Status.NOT_FOUND;
import static java.util.Optional.ofNullable;
import static orders.service.ReactiveUtils.toMono;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrdersServiceImpl extends OrdersServiceImplBase {
    OrderRepository orderRepository;
    ReserveServiceStub reserveClient;
    PaymentsServiceStub paymentsClient;

    private static OrdersResponse toOrdersResponse(List<OrderEntity> orders) {
        return OrdersResponse.newBuilder()
                .addAllOrders(orders.stream().map(OrdersServiceImpl::toOrderResponse).toList())
                .build();
    }

    private static OrderResponse toOrderResponse(OrderEntity orderEntity) {
        return OrderResponse.newBuilder()
                .setId(ofNullable(orderEntity.id()).map(UUID::toString).orElse(null))
                .build();
    }

    private static <T, ID> Mono<T> notFoundById(ID id) {
        return error(() -> NOT_FOUND.withDescription(String.valueOf(id)).asRuntimeException());
    }

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        var paymentRequest = NewPaymentRequest.newBuilder().build();
        var reserveRequest = NewReserveRequest.newBuilder().build();

        var reserveResponseMono = toMono(reserveRequest, reserveClient::create);
        var paymentResponseMono = toMono(paymentRequest, paymentsClient::create);

//        reserveResponseMono.map(reserveResponse -> {
//            return paymentResponseMono.map(paymentResponse -> {
//
//            });
//        })

        paymentResponseMono.zipWith(reserveResponseMono).subscribe(responses -> {
            var paymentResponse = responses.getT1();
            var reserveResponse = responses.getT2();

            var response = OrderCreateResponse.newBuilder().build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }, error -> {
            responseObserver.onError(error);
            responseObserver.onCompleted();
        });
    }

    @Override
    public void get(OrderGetRequest request, StreamObserver<OrderResponse> responseObserver) {
        just(request).map(OrderGetRequest::getId).map(UUID::fromString).flatMap(id -> {
            return orderRepository.findById(id).switchIfEmpty(notFoundById(id));
        }).subscribe(
                orderEntity -> responseObserver.onNext(toOrderResponse(orderEntity)),
                responseObserver::onError, responseObserver::onCompleted
        );
    }

    @Override
    public void cancel(OrderCancelRequest request, StreamObserver<OrderCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void search(OrderFindRequest request, StreamObserver<OrdersResponse> responseObserver) {
        orderRepository.findAll().subscribe(
                orderEntity -> responseObserver.onNext(toOrdersResponse(orderEntity)),
                responseObserver::onError,
                responseObserver::onCompleted);
    }
}
