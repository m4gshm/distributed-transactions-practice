package io.github.m4gshm.orders.service;

import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders.OrderApproveRequest;
import orders.v1.Orders.OrderApproveResponse;
import orders.v1.Orders.OrderCancelRequest;
import orders.v1.Orders.OrderCancelResponse;
import orders.v1.Orders.OrderCreateRequest;
import orders.v1.Orders.OrderCreateResponse;
import orders.v1.Orders.OrderGetRequest;
import orders.v1.Orders.OrderGetResponse;
import orders.v1.Orders.OrderListRequest;
import orders.v1.Orders.OrderListResponse;
import orders.v1.Orders.OrderReleaseRequest;
import orders.v1.Orders.OrderReleaseResponse;
import orders.v1.OrdersServiceGrpc;
import org.springframework.stereotype.Service;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class GrpcOrdersServiceImpl extends OrdersServiceGrpc.OrdersServiceImplBase {
    GrpcReactive grpc;
    OrdersService ordersService;

    @Override
    public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.create(request.getBody(), request.getTwoPhaseCommit()));
    }

    @Override
    public void approve(OrderApproveRequest request, StreamObserver<OrderApproveResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.approve(request.getId(), request.getTwoPhaseCommit()));
    }

    @Override
    public void release(OrderReleaseRequest request, StreamObserver<OrderReleaseResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.release(request.getId(), request.getTwoPhaseCommit()));
    }

    @Override
    public void cancel(OrderCancelRequest request, StreamObserver<OrderCancelResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.cancel(request.getId(), request.getTwoPhaseCommit()));
    }

    @Override
    public void get(OrderGetRequest request, StreamObserver<OrderGetResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.get(request.getId()));
    }

    @Override
    public void list(OrderListRequest request, StreamObserver<OrderListResponse> responseObserver) {
        grpc.subscribe(responseObserver, ordersService.list());
    }

}
