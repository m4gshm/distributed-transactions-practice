package io.github.m4gshm.orders.data.storage;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.storage.CrudStorage;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface OrderStorage extends CrudStorage<Order, String> {

    Mono<List<Order>> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses);
}
