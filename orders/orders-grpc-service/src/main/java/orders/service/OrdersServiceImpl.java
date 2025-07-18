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
import payments.v1.Payments.NewPaymentRequest;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceStub;
import reactor.core.publisher.Mono;
import reserve.v1.Reserve.NewReserveRequest;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;

import java.util.UUID;

import static orders.data.storage.r2dbc.TwoPhaseTransaction.commit;
import static orders.data.storage.r2dbc.TwoPhaseTransaction.rollback;
import static orders.service.GrpcUtils.subscribe;
import static orders.service.OrdersServiceUtils.notFoundById;
import static orders.service.OrdersServiceUtils.string;
import static orders.service.OrdersServiceUtils.toDelivery;
import static orders.service.OrdersServiceUtils.uuid;
import static orders.service.ReactiveUtils.toMono;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.just;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrdersServiceImpl extends OrdersServiceImplBase {
    DSLContext dsl;
    OrderStorage orderRepository;
    ReserveServiceStub reserveClient;
    PaymentsServiceStub paymentsClient;

    private static OrderCreateResponse toOrderCreateResponse(Order order) {
        return OrderCreateResponse.newBuilder().setId(string(order.id())).build();
    }

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> response) {
        var twoPhasedTransaction = true;

        subscribe(response, Mono.fromSupplier(UUID::randomUUID).map(OrdersServiceUtils::string).flatMap(orderId -> {
            var itemsList = request.getItemsList();
            var items = itemsList.stream().map(OrdersServiceUtils::toItem).toList();

            var amount = items.stream().mapToDouble(Order.Item::cost).sum();

            var paymentRequest = NewPaymentRequest.newBuilder().setAmount(amount).build();
            var reserveRequest = NewReserveRequest.newBuilder().addAllItems(items.stream()
                            .map(OrdersServiceUtils::toReserveItem).toList())
                    .build();

            var reserveRoutine = toMono(reserveRequest, reserveClient::create);
            var paymentRoutine = toMono(paymentRequest, paymentsClient::create);

            return paymentRoutine.zipWith(reserveRoutine).flatMap(responses -> {
                var paymentResponse = responses.getT1();
                var reserveResponse = responses.getT2();
                return orderRepository.save(Order.builder()
                        .id(string(orderId))
                        .paymentId(uuid(paymentResponse.getId()))
                        .reserveId(uuid(reserveResponse.getId()))
                        .customerId(uuid(request.getCustomerId()))
                        .delivery(toDelivery(request.getDelivery()))
                        .items(items)
                        .build(), twoPhasedTransaction);
            }).flatMap(order -> {
                return !twoPhasedTransaction ? Mono.just(order) : commit(dsl, string(orderId)).thenReturn(order);
            }).onErrorResume(throwable -> {
                //rollback order
                //rollback payment
                //rollback reserve
                var error = Mono.<Order>error(throwable);
                return !twoPhasedTransaction ? error : rollback(dsl, string(orderId)).then(defer(() -> {
                    return error;
                })).switchIfEmpty(error);
            });
        }).map(OrdersServiceImpl::toOrderCreateResponse));
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
