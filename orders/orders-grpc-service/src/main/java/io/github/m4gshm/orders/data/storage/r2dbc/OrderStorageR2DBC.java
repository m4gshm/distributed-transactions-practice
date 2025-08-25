package io.github.m4gshm.orders.data.storage.r2dbc;

import static io.github.m4gshm.EnumWithCodeUtils.getCode;
import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqMapperUtils.toOrder;
import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqQueryUtils.orNow;
import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqQueryUtils.selectOrdersJoinDelivery;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static lombok.AccessLevel.PRIVATE;
import static orders.data.access.jooq.Tables.DELIVERY;
import static orders.data.access.jooq.Tables.ITEMS;
import static orders.data.access.jooq.Tables.ORDERS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.value;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.from;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import io.github.m4gshm.EnumWithCode;
import io.github.m4gshm.LogUtils;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.model.Order.Status;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.utils.Jooq;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageR2DBC implements OrderStorage {
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
    public Mono<List<Order>> findByClientIdAndStatuses(String clientId, Collection<Status> statuses) {
        return jooq.inTransaction(dsl -> {
            var selectOrder = selectOrdersJoinDelivery(dsl).where(ORDERS.CUSTOMER_ID.eq(clientId)
                    .and(ORDERS.STATUS.in(statuses.stream()
                            .map(EnumWithCode::getCode)
                            .toList())));
            return Flux.from(selectOrder).collectList().flatMap(orders -> {
                var orderIds = orders.stream().map(record -> record.get(ORDERS.ID)).toList();
                var selectItems = selectAllFrom(dsl, ITEMS).where(ITEMS.ORDER_ID.in(orderIds));
                return Flux.from(selectItems).collectList().map(items -> {
                    var itemsGroupedByOrderId = items.stream().collect(groupingBy(item -> item.get(ITEMS.ORDER_ID)));
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
            var selectItems = selectAllFrom(dsl, ITEMS).where(ITEMS.ORDER_ID.eq(id));
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
            var orderStatus = getCode(order.status());
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
                    .set(ORDERS.RESERVE_ID, coalesce(value(order.reserveId()), ORDERS.RESERVE_ID))
                    .set(ORDERS.PAYMENT_ID, coalesce(value(order.paymentId()), ORDERS.PAYMENT_ID))
            );

            var delivery = order.delivery();

            final Mono<Integer> mergeDelivery;
            if (delivery == null) {
                mergeDelivery = Mono.empty();
            } else {
                var deliveryType = getCode(delivery.type());
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
                    .map(item -> dsl.insertInto(ITEMS)
                            .set(ITEMS.ORDER_ID, order.id())
                            .set(ITEMS.ID, item.id())
                            .set(ITEMS.AMOUNT, item.amount())
                            .onDuplicateKeyUpdate()
                            .set(ITEMS.AMOUNT, item.amount()))
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
