package orders.service;

import io.grpc.stub.StreamObserver;
import orders.v1.OrderServiceGrpc;
import orders.v1.Orders.*;

public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        responseObserver.onNext(OrderCreateResponse.newBuilder().build());
        responseObserver.onCompleted();
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
    public void find(OrderFindRequest request, StreamObserver<OrderFindResponse> responseObserver) {
        super.find(request, responseObserver);
    }
}
