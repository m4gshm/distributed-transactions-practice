package io.github.m4gshm.orders.data.storage.r2dbc;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.orders.data.access.jooq.Tables;
import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.ReactiveOrderStorage;
import io.github.m4gshm.tracing.TraceService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqMapperUtils.toOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.insertItem;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeDelivery;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectItemsByOrderId;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDelivery;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryByCustomerIdAndStatusIn;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryById;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Flux.fromIterable;
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

    @Override
    public Mono<List<Order>> findAll() {
        return jooq.inTransaction(dsl -> {
            return Flux.from(selectOrdersJoinDelivery(dsl))
                    .map(record -> toOrder(record, record, List.of()))
                    .collectList();
        });
    }

    @Override
    public Mono<List<Order>> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses) {
        return jooq.inTransaction(dsl -> {
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
        return jooq.inTransaction(dsl -> {
            var selectOrder = selectOrdersJoinDeliveryById(dsl, id);
            var selectItems = selectItemsByOrderId(dsl, id);
            var local = traceService.getLocal();
            return from(selectOrder).zipWith(Flux.from(selectItems).collectList(), (order, items) -> {
                return toOrder(order, order, items);
            });
        });
    }

    private <T> Mono<T> log(String category, Mono<T> mono) {
        return mono;
//        return LogUtils.log(getClass(), category, mono);
    }

    @Override
    public Mono<Order> save(DSLContext dsl, Order order) {
        var delivery = order.delivery();

        var mergeDelivery = delivery != null
                ? from(mergeDelivery(dsl, order, delivery))
                : Mono.<Integer>empty();

        var mergeAllItems = fromIterable(ofNullable(order.items()).orElse(List.of())
                .stream()
                .map(item -> insertItem(dsl, order, item))
                .map(Mono::from)
                .toList()).flatMap(i1 -> i1)
                .reduce(Integer::sum);

        return from(mergeOrder(dsl, order)).flatMap(count -> {
//            log.debug("stored order rows {}", count);
            return log("mergeDelivery", mergeDelivery.doOnSuccess(deliveryRows -> {
//                log.debug("stored delivery rows {}", deliveryRows);
            })).zipWith(log("mergeAllItems", mergeAllItems.doOnSuccess(itemRows -> {
//                log.debug("stored item rows {}", itemRows);
            })), (deliveryRows, itemRows) -> order).thenReturn(order);
        });
    }

    @Override
    public Mono<Order> save(Order order) {
        return jooq.inTransaction(dsl -> {
            return save(dsl, order);
        });
    }

}
