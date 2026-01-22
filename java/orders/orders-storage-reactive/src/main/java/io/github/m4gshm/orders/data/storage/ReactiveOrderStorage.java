package io.github.m4gshm.orders.data.storage;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.storage.Page;
import io.github.m4gshm.storage.ReactiveCrudStorage;
import io.github.m4gshm.storage.ReactivePageableReadOperations;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface ReactiveOrderStorage extends ReactiveCrudStorage<Order, String>,
        ReactivePageableReadOperations<Order, String> {

    Mono<List<Order>> findAll(Page page, OrderStatus status);

    Mono<List<Order>> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses);

    Mono<Order> saveOrderOnly(Order order);
}
