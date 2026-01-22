package io.github.m4gshm.orders.data.storage;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.storage.CrudStorage;
import io.github.m4gshm.storage.Page;
import io.github.m4gshm.storage.PageableReadOperations;

import java.util.Collection;
import java.util.List;

public interface OrderStorage extends CrudStorage<Order, String>, PageableReadOperations<Order, String> {

    List<Order> findAll(Page page, OrderStatus status);

    List<Order> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses);

    Order saveOrderOnly(Order order);
}
