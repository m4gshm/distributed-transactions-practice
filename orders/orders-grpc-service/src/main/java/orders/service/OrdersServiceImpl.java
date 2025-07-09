package orders.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import orders.v1.Orders.OrderCancelRequest;
import orders.v1.Orders.OrderCancelResponse;
import orders.v1.Orders.OrderCreateRequest;
import orders.v1.Orders.OrderCreateResponse;
import orders.v1.Orders.OrderFindRequest;
import orders.v1.Orders.OrderFindResponse;
import orders.v1.Orders.OrderGetRequest;
import orders.v1.Orders.OrderGetResponse;
import orders.v1.OrdersServiceGrpc.OrdersServiceImplBase;
import payments.v1.Payments.NewPaymentRequest;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceStub;
import reserve.v1.Reserve.NewReserveRequest;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;

import static orders.service.ReactiveUtils.toMono;

@RequiredArgsConstructor
public class OrdersServiceImpl extends OrdersServiceImplBase {
    private final ReserveServiceStub reserveClient;
    private final PaymentsServiceStub paymentsClient;

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
    public void get(OrderGetRequest request, StreamObserver<OrderGetResponse> responseObserver) {
        var id = request.getId();
        responseObserver.onNext(OrderGetResponse.newBuilder().setId(id).build());
        responseObserver.onCompleted();
    }

    @Override
    public void cancel(OrderCancelRequest request, StreamObserver<OrderCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void search(OrderFindRequest request, StreamObserver<OrderFindResponse> responseObserver) {
        super.search(request, responseObserver);
    }
}
