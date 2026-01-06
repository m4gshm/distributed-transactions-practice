package io.github.m4gshm.orders.data.storage;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.storage.CrudStorage;
import org.jooq.DSLContext;

import java.util.Collection;
import java.util.List;

public interface OrderStorage extends CrudStorage<Order, String> {

    List<Order> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses);

    Order save(DSLContext dsl, Order order);
}
