package io.github.m4gshm.orders.data.storage.r2dbc;

import io.github.m4gshm.LogUtils;
import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.orders.data.access.jooq.Tables;
import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.access.jooq.tables.Delivery;
import io.github.m4gshm.orders.data.access.jooq.tables.Item;
import io.github.m4gshm.orders.data.access.jooq.tables.Orders;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.impl.DSL;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqMapperUtils.toOrder;
import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqQueryUtils.orNow;
import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqQueryUtils.selectOrdersJoinDelivery;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static lombok.AccessLevel.PRIVATE;
import static org.jooq.impl.DSL.value;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageR2DBC implements OrderStorage {
    public static final Item ITEM = Tables.ITEM;
    public static final Orders ORDERS = Tables.ORDERS;
    public static final Delivery DELIVERY = Tables.DELIVERY;
    @Getter
    private final Class<Order> entityClass = Order.class;
    Jooq jooq;

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
            var selectOrder = selectOrdersJoinDelivery(dsl).where(ORDERS.CUSTOMER_ID.eq(clientId)
                    .and(ORDERS.STATUS.in(statuses)));
            return Flux.from(selectOrder).collectList().flatMap(orders -> {
                var orderIds = orders.stream().map(record -> record.get(ORDERS.ID)).toList();
                var selectItems = selectAllFrom(dsl, ITEM).where(ITEM.ORDER_ID.in(orderIds));
                return Flux.from(selectItems).collectList().map(items -> {
                    var itemsGroupedByOrderId = items.stream()
                            .collect(groupingBy(item -> item.get(ITEM.ORDER_ID)));
                    return orders.stream().map(order -> {
                        var orderItems = itemsGroupedByOrderId.get(order.get(ORDERS.ID));
                        return toOrder(order, order, orderItems);
                    }).toList();
                });
            });
        });
    }

    @Override
    public Mono<Order> findById(String id) {
        return jooq.inTransaction(dsl -> {
            var selectOrder = selectOrdersJoinDelivery(dsl).where(ORDERS.ID.eq(id));
            var selectItems = selectAllFrom(dsl, ITEM).where(ITEM.ORDER_ID.eq(id));
            return from(selectOrder).zipWith(Flux.from(selectItems).collectList(), (order, items) -> {
                return toOrder(order, order, items);
            });
        });
    }

    private <T> Mono<T> log(String category, Mono<T> mono) {
        return LogUtils.log(getClass(), category, mono);
    }

    @Override
    public Mono<Order> save(Order order) {
        return jooq.inTransaction(dsl -> {
            var orderStatus = order.status();
            var mergeOrder = from(dsl.insertInto(ORDERS)
                    .set(ORDERS.ID, order.id())
                    .set(ORDERS.STATUS, orderStatus)
                    .set(ORDERS.CREATED_AT, orNow(order.createdAt()))
                    .set(ORDERS.CUSTOMER_ID, order.customerId())
                    .set(ORDERS.RESERVE_ID, order.reserveId())
                    .set(ORDERS.PAYMENT_ID, order.paymentId())
                    .set(ORDERS.PAYMENT_TRANSACTION_ID, order.paymentTransactionId())
                    .set(ORDERS.RESERVE_TRANSACTION_ID, order.reserveTransactionId())
                    .onDuplicateKeyUpdate()
                    .set(ORDERS.STATUS, orderStatus)
                    .set(ORDERS.UPDATED_AT, orNow(order.updatedAt()))
                    .set(ORDERS.RESERVE_ID, DSL.coalesce(value(order.reserveId()), ORDERS.RESERVE_ID))
                    .set(ORDERS.PAYMENT_ID, DSL.coalesce(value(order.paymentId()), ORDERS.PAYMENT_ID))
            );

            var delivery = order.delivery();

            final Mono<Integer> mergeDelivery;
            if (delivery == null) {
                mergeDelivery = Mono.empty();
            } else {
                var deliveryType = delivery.type();
                mergeDelivery = from(dsl.insertInto(DELIVERY)
                        .set(DELIVERY.ORDER_ID, order.id())
                        .set(DELIVERY.ADDRESS, delivery.address())
                        .set(DELIVERY.TYPE, deliveryType)
                        .onDuplicateKeyUpdate()
                        .set(DELIVERY.ADDRESS, delivery.address())
                        .set(DELIVERY.TYPE, deliveryType));
            }
            var mergeAllItems = fromIterable(ofNullable(order.items())
                    .orElse(List.of())
                    .stream()
                    .map(item -> dsl.insertInto(Tables.ITEM)
                            .set(Tables.ITEM.ORDER_ID, order.id())
                            .set(Tables.ITEM.ID, item.id())
                            .set(Tables.ITEM.AMOUNT, item.amount())
                            .onDuplicateKeyUpdate()
                            .set(Tables.ITEM.AMOUNT, item.amount()))
                    .map(Mono::from)
                    .toList()).flatMap(i1 -> i1)
                    .reduce(Integer::sum);

            return mergeOrder.flatMap(count -> {
                log.debug("stored order rows {}", count);
                return log("mergeDelivery", mergeDelivery.doOnSuccess(deliveryRows -> {
                    log.debug("stored delivery rows {}", deliveryRows);
                })).zipWith(log("mergeAllItems", mergeAllItems.doOnSuccess(itemRows -> {
                    log.debug("stored item rows {}", itemRows);
                })), (deliveryRows, itemRows) -> order).thenReturn(order);
            });
        });
    }
}
