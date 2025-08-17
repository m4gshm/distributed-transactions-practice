package io.github.m4gshm.orders.service;

import orders.v1.Orders.OrderApproveResponse;
import orders.v1.Orders.OrderCancelResponse;
import orders.v1.Orders.OrderCreateRequest.OrderCreate;
import orders.v1.Orders.OrderCreateResponse;
import orders.v1.Orders.OrderGetResponse;
import orders.v1.Orders.OrderListResponse;
import orders.v1.Orders.OrderReleaseResponse;
import reactor.core.publisher.Mono;

public interface OrdersService {
    Mono<OrderApproveResponse> approve(String orderId, boolean twoPhaseCommit);

    Mono<OrderCancelResponse> cancel(String orderId, boolean twoPhaseCommit);

    Mono<OrderCreateResponse> create(OrderCreate createRequest, boolean twoPhaseCommit);

    Mono<OrderGetResponse> get(String orderId);

    Mono<OrderListResponse> list();

    Mono<OrderReleaseResponse> release(String orderId, boolean twoPhaseCommit);
}
