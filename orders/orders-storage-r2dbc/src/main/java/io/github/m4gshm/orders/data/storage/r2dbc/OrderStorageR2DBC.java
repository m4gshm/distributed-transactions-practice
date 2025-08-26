package io.github.m4gshm.orders.data.storage.r2dbc;

import static io.github.m4gshm.EnumWithCodeUtils.getCode;
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

import java.util.Collection;
import java.util.List;

import org.jooq.impl.DSL;
import org.springframework.validation.annotation.Validated;

import io.github.m4gshm.EnumWithCode;
import io.github.m4gshm.LogUtils;
import io.github.m4gshm.orders.data.access.jooq.Tables;
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
            var selectOrder = selectOrdersJoinDelivery(dsl).where(Tables.ORDERS.CUSTOMER_ID.eq(clientId)
                    .and(Tables.ORDERS.STATUS.in(statuses.stream()
                            .map(EnumWithCode::getCode)
                            .toList())));
            return Flux.from(selectOrder).collectList().flatMap(orders -> {
                var orderIds = orders.stream().map(record -> record.get(Tables.ORDERS.ID)).toList();
                var selectItems = selectAllFrom(dsl, Tables.ITEMS).where(Tables.ITEMS.ORDER_ID.in(orderIds));
                return Flux.from(selectItems).collectList().map(items -> {
                    var itemsGroupedByOrderId = items.stream()
                            .collect(groupingBy(item -> item.get(Tables.ITEMS.ORDER_ID)));
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
            var selectOrder = selectOrdersJoinDelivery(dsl).where(Tables.ORDERS.ID.eq(id));
            var selectItems = selectAllFrom(dsl, Tables.ITEMS).where(Tables.ITEMS.ORDER_ID.eq(id));
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
            var mergeOrder = from(dsl.insertInto(Tables.ORDERS)
                    .set(Tables.ORDERS.ID, order.id())
                    .set(Tables.ORDERS.STATUS, orderStatus)
                    .set(Tables.ORDERS.CREATED_AT, orNow(order.createdAt()))
                    .set(Tables.ORDERS.CUSTOMER_ID, order.customerId())
                    .set(Tables.ORDERS.RESERVE_ID, order.reserveId())
                    .set(Tables.ORDERS.PAYMENT_ID, order.paymentId())
                    .set(Tables.ORDERS.PAYMENT_TRANSACTION_ID, order.paymentTransactionId())
                    .set(Tables.ORDERS.RESERVE_TRANSACTION_ID, order.reserveTransactionId())
                    .onDuplicateKeyUpdate()
                    .set(Tables.ORDERS.STATUS, orderStatus)
                    .set(Tables.ORDERS.UPDATED_AT, orNow(order.updatedAt()))
                    .set(Tables.ORDERS.RESERVE_ID, DSL.coalesce(value(order.reserveId()), Tables.ORDERS.RESERVE_ID))
                    .set(Tables.ORDERS.PAYMENT_ID, DSL.coalesce(value(order.paymentId()), Tables.ORDERS.PAYMENT_ID))
            );

            var delivery = order.delivery();

            final Mono<Integer> mergeDelivery;
            if (delivery == null) {
                mergeDelivery = Mono.empty();
            } else {
                var deliveryType = getCode(delivery.type());
                mergeDelivery = from(dsl.insertInto(Tables.DELIVERY)
                        .set(Tables.DELIVERY.ORDER_ID, order.id())
                        .set(Tables.DELIVERY.ADDRESS, delivery.address())
                        .set(Tables.DELIVERY.TYPE, deliveryType)
                        .onDuplicateKeyUpdate()
                        .set(Tables.DELIVERY.ADDRESS, delivery.address())
                        .set(Tables.DELIVERY.TYPE, deliveryType));
            }
            var mergeAllItems = fromIterable(ofNullable(order.items())
                    .orElse(List.of())
                    .stream()
                    .map(item -> dsl.insertInto(Tables.ITEMS)
                            .set(Tables.ITEMS.ORDER_ID, order.id())
                            .set(Tables.ITEMS.ID, item.id())
                            .set(Tables.ITEMS.AMOUNT, item.amount())
                            .onDuplicateKeyUpdate()
                            .set(Tables.ITEMS.AMOUNT, item.amount()))
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
