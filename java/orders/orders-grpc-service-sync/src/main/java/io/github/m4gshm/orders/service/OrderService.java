package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.storage.PageableReadOperations;
import io.github.m4gshm.storage.PageableReadOperations.Page;
import orders.v1.OrderServiceOuterClass.OrderApproveResponse;
import orders.v1.OrderServiceOuterClass.OrderCancelResponse;
import orders.v1.OrderServiceOuterClass.OrderCreateRequest.OrderCreate;
import orders.v1.OrderServiceOuterClass.OrderCreateResponse;
import orders.v1.OrderServiceOuterClass.OrderGetResponse;
import orders.v1.OrderServiceOuterClass.OrderListResponse;
import orders.v1.OrderServiceOuterClass.OrderReleaseResponse;
import orders.v1.OrderServiceOuterClass.OrderResumeResponse;

public interface OrderService {
    OrderApproveResponse approve(String orderId, boolean twoPhaseCommit);

    OrderCancelResponse cancel(String orderId, boolean twoPhaseCommit);

    OrderCreateResponse create(OrderCreate createRequest, boolean twoPhaseCommit);

    OrderGetResponse get(String orderId);

    OrderListResponse list(Page page, OrderStatus status);

    OrderReleaseResponse release(String orderId, boolean twoPhaseCommit);

    OrderResumeResponse resume(String orderId, boolean twoPhaseCommit);
}
