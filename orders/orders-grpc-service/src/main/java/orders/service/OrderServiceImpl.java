package orders.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import orders.v1.OrderServiceGrpc;
import orders.v1.Orders.OrderCancelRequest;
import orders.v1.Orders.OrderCancelResponse;
import orders.v1.Orders.OrderCreateRequest;
import orders.v1.Orders.OrderCreateResponse;
import orders.v1.Orders.OrderFindRequest;
import orders.v1.Orders.OrderFindResponse;
import orders.v1.Orders.OrderGetRequest;
import orders.v1.Orders.OrderGetResponse;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.Reserve;

import static orders.service.ReactiveUtils.toMono;

@RequiredArgsConstructor
public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
    private final ReserveServiceGrpc.ReserveServiceStub reserveClient;

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        var reserveRequest = Reserve.NewReserveRequest.newBuilder().build();
        toMono(reserveRequest, reserveClient::create).subscribe(response -> {
            responseObserver.onNext(OrderCreateResponse.newBuilder().build());
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
