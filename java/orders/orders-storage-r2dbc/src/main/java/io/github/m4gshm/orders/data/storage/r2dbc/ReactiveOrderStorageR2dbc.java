package io.github.m4gshm.orders.data.storage.r2dbc;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.orders.data.access.jooq.Tables;
import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.ReactiveOrderStorage;
import io.github.m4gshm.storage.Page;
import io.github.m4gshm.tracing.TraceService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqMapperUtils.toOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeOrderFullBatch;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectItemsByOrderId;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryByCustomerIdAndStatusIn;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryById;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryPaged;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.stream.Collectors.groupingBy;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactiveOrderStorageR2dbc implements ReactiveOrderStorage {
    @Getter
    Class<Order> entityClass = Order.class;
    ReactiveJooq jooq;
    TraceService traceService;

    private static String getOp(String op) {
        return Order.class.getSimpleName() + ":" + op;
    }

    @Override
    public Mono<List<Order>> findAll() {
        return findAll(null);
    }

    @Override
    public Mono<List<Order>> findAll(Page page) {
        return findAll(page, null);
    }

    @Override
    public Mono<List<Order>> findAll(Page page, OrderStatus status) {
        return Mono.defer(() -> {
            var num = Page.getNum(page);
            if (num < 0) {
                throw new IllegalArgumentException("page.num cannot be less than 0");
            }

            var size = Page.getSize(page);
            if (size <= 0) {
                throw new IllegalArgumentException("page.size must be more than 0");
            }
            return jooq.supportTransaction(getOp("findAll"), dsl -> {
                return Flux.from(selectOrdersJoinDeliveryPaged(dsl, status, size, num))
                        .map(record -> toOrder(record, record, List.of()))
                        .collectList();
            });
        });
    }

    @Override
    public Mono<List<Order>> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses) {
        return jooq.supportTransaction(getOp("findByClientIdAndStatuses"), dsl -> {
            var selectOrder = selectOrdersJoinDeliveryByCustomerIdAndStatusIn(dsl,
                    clientId,
                    statuses
            );
            return Flux.from(selectOrder).collectList().flatMap(orders -> {
                var orderIds = orders.stream().map(record -> record.get(Tables.ORDERS.ID)).toList();
                var selectItems = selectAllFrom(dsl, Tables.ITEM).where(Tables.ITEM.ORDER_ID.in(orderIds));
                return Flux.from(selectItems).collectList().map(items -> {
                    var itemsGroupedByOrderId = items.stream()
                            .collect(groupingBy(item -> item.get(Tables.ITEM.ORDER_ID)));
                    return orders.stream().map(order -> {
                        var orderItems = itemsGroupedByOrderId.get(order.get(Tables.ORDERS.ID));
                        return toOrder(order, order, orderItems);
                    }).toList();
                });
            });
        });
    }

    @Override
    public Mono<Order> findById(String id) {
        return jooq.supportTransaction(getOp("findById"), dsl -> {
            var selectOrder = selectOrdersJoinDeliveryById(dsl, id);
            var selectItems = selectItemsByOrderId(dsl, id);
            return from(selectOrder).zipWith(Flux.from(selectItems).collectList(), (order, items) -> {
                return toOrder(order, order, items);
            });
        });
    }

    @Override
    public Mono<Order> save(Order order) {
        return jooq.supportTransaction(getOp("save"), dsl -> {
            Mono<Void> merge;
//            merge = Flux.concat(mergeOrderFullQueries(dsl, order).stream().map(Mono::from).toList()).then();
            merge = Mono.from(mergeOrderFullBatch(dsl, order)).then();
            return merge.thenReturn(order);
        });
    }

    @Override
    public Mono<Order> saveOrderOnly(Order order) {
        return jooq.supportTransaction(getOp("saveOrderOnly"), dsl -> {
            return from(mergeOrder(dsl, order)).thenReturn(order);
        });
    }
}
