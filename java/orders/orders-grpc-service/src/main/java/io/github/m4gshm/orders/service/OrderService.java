package io.github.m4gshm.orders.service;

import orders.v1.OrderApi.OrderApproveResponse;
import orders.v1.OrderApi.OrderCancelResponse;
import orders.v1.OrderApi.OrderCreateRequest.OrderCreate;
import orders.v1.OrderApi.OrderCreateResponse;
import orders.v1.OrderApi.OrderGetResponse;
import orders.v1.OrderApi.OrderListResponse;
import orders.v1.OrderApi.OrderReleaseResponse;
import orders.v1.OrderApi.OrderResumeResponse;
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
