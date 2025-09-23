package io.github.m4gshm.orders.service;

import orders.v1.OrderServiceOuterClass.OrderApproveResponse;
import orders.v1.OrderServiceOuterClass.OrderCancelResponse;
import orders.v1.OrderServiceOuterClass.OrderCreateRequest.OrderCreate;
import orders.v1.OrderServiceOuterClass.OrderCreateResponse;
import orders.v1.OrderServiceOuterClass.OrderGetResponse;
import orders.v1.OrderServiceOuterClass.OrderListResponse;
import orders.v1.OrderServiceOuterClass.OrderReleaseResponse;
import orders.v1.OrderServiceOuterClass.OrderResumeResponse;
import reactor.core.publisher.Mono;

public interface OrderService {
    Mono<OrderApproveResponse> approve(String orderId, boolean twoPhaseCommit);

    Mono<OrderCancelResponse> cancel(String orderId, boolean twoPhaseCommit);

    Mono<OrderCreateResponse> create(OrderCreate createRequest, boolean twoPhaseCommit);

    Mono<OrderGetResponse> get(String orderId);

    Mono<OrderListResponse> list();

    Mono<OrderReleaseResponse> release(String orderId, boolean twoPhaseCommit);

    Mono<OrderResumeResponse> resume(String orderId, boolean twoPhaseCommit);
}
