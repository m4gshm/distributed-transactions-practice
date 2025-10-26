package io.github.m4gshm.orders.service;

import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.OrderServiceGrpc;
import orders.v1.OrderServiceOuterClass;
import orders.v1.OrderServiceOuterClass.OrderApproveRequest;
import orders.v1.OrderServiceOuterClass.OrderCancelRequest;
import orders.v1.OrderServiceOuterClass.OrderCreateRequest;
import orders.v1.OrderServiceOuterClass.OrderCreateResponse;
import orders.v1.OrderServiceOuterClass.OrderGetRequest;
import orders.v1.OrderServiceOuterClass.OrderGetResponse;
import orders.v1.OrderServiceOuterClass.OrderListRequest;
import orders.v1.OrderServiceOuterClass.OrderListResponse;
import orders.v1.OrderServiceOuterClass.OrderReleaseRequest;
import orders.v1.OrderServiceOuterClass.OrderReleaseResponse;
import orders.v1.OrderServiceOuterClass.OrderResumeRequest;
import org.springframework.stereotype.Service;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class GrpcOrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
    GrpcReactive grpc;
    OrderService ordersService;

    @Override
    public void approve(OrderApproveRequest request,
                        StreamObserver<OrderServiceOuterClass.OrderApproveResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.approve(request.getId(), request.getTwoPhaseCommit()));
    }

    @Override
    public void cancel(OrderCancelRequest request,
                       StreamObserver<OrderServiceOuterClass.OrderCancelResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.cancel(request.getId(), request.getTwoPhaseCommit()));
    }

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.create(request.getBody(), request.getTwoPhaseCommit()));
    }

    @Override
    public void get(OrderGetRequest request, StreamObserver<OrderGetResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.get(request.getId()));
    }

    @Override
    public void list(OrderListRequest request, StreamObserver<OrderListResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.list());
    }

    @Override
    public void release(OrderReleaseRequest request, StreamObserver<OrderReleaseResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.release(request.getId(), request.getTwoPhaseCommit()));
    }

    @Override
    public void resume(OrderResumeRequest request,
                       StreamObserver<OrderServiceOuterClass.OrderResumeResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.resume(request.getId(), request.getTwoPhaseCommit()));
    }

}
