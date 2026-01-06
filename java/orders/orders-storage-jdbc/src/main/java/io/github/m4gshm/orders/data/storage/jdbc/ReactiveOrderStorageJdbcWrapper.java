package io.github.m4gshm.orders.data.storage.jdbc;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.data.storage.ReactiveOrderStorage;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static reactor.core.scheduler.Schedulers.boundedElastic;

@RequiredArgsConstructor
public class ReactiveOrderStorageJdbcWrapper implements ReactiveOrderStorage {
    private final OrderStorage orderStorage;

    private static <T> Mono<T> subscribe(Supplier<T> supplier) {
        return Mono.fromSupplier(supplier).subscribeOn(boundedElastic());
    }

    @Override
    public Mono<List<Order>> findAll() {
        return subscribe(orderStorage::findAll);
    }

    @Override
    public Mono<List<Order>> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses) {
        return subscribe(() -> orderStorage.findByClientIdAndStatuses(clientId, statuses));
    }

    @Override
    public Mono<Order> findById(String id) {
        return subscribe(() -> orderStorage.findById(id));
    }

    @Override
    public Class<Order> getEntityClass() {
        return orderStorage.getEntityClass();
    }

    @Override
    public Mono<Order> save(DSLContext dsl, Order order) {
        return subscribe(() -> orderStorage.save(dsl, order));
    }

    @Override
    public Mono<Order> save(Order order) {
        return subscribe(() -> orderStorage.save(order));
    }
}
