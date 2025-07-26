package io.github.m4gshm.orders.data.storage.r2dbc;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.github.m4gshm.jooq.utils.Query.selectAllFrom;
import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqMapperUtils.toOrder;
import static io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageJooqQueryUtils.selectOrdersJoinDelivery;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static orders.data.access.jooq.Tables.DELIVERY;
import static orders.data.access.jooq.Tables.ITEMS;
import static orders.data.access.jooq.Tables.ORDERS;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageR2DBC implements OrderStorage {
    @Getter
    private final Class<Order> entityClass = Order.class;
    Jooq jooq;

    @Override
    public Mono<List<Order>> findAll() {
        return jooq.transactional(dsl -> {
            return Flux.from(selectOrdersJoinDelivery(dsl))
                    .map(record -> toOrder(record, record, List.of()))
                    .collectList();
        });
    }

    @Override
    public Mono<Order> findById(String id) {
        return jooq.transactional(dsl -> {
            var selectOrder = selectOrdersJoinDelivery(dsl).where(ORDERS.ID.eq(id));
            var selectItems = selectAllFrom(dsl, ITEMS).where(ITEMS.ORDER_ID.eq(id));
            return from(selectOrder).zipWith(Flux.from(selectItems).collectList(), (order, items) -> {
                return toOrder(order, order, items);
            });
        });
    }

    @Override
    public Mono<Order> save(Order order) {
        return jooq.transactional(dsl -> {
            var mergeOrder = from(dsl.insertInto(ORDERS)
                    .set(ORDERS.ID, order.id())
                    .set(ORDERS.CREATED_AT, OrderStorageJooqQueryUtils.orNow(order.createdAt()))
                    .set(ORDERS.CUSTOMER_ID, order.customerId())
                    .set(ORDERS.RESERVE_ID, order.reserveId())
                    .set(ORDERS.PAYMENT_ID, order.paymentId())
                    .onDuplicateKeyUpdate()
                    .set(ORDERS.UPDATED_AT, OrderStorageJooqQueryUtils.orNow(order.updatedAt()))
                    .set(ORDERS.CUSTOMER_ID, order.customerId())
                    .set(ORDERS.RESERVE_ID, order.reserveId())
                    .set(ORDERS.PAYMENT_ID, order.paymentId()));

            var delivery = order.delivery();

            final Mono<Integer> mergeDelivery;
            if (delivery == null) {
                mergeDelivery = Mono.empty();
            } else {
                var code = delivery.type().getCode();
                mergeDelivery = from(dsl.insertInto(DELIVERY)
                        .set(DELIVERY.ORDER_ID, order.id())
                        .set(DELIVERY.ADDRESS, delivery.address())
                        .set(DELIVERY.TYPE, code)
                        .onDuplicateKeyUpdate()
                        .set(DELIVERY.ADDRESS, delivery.address())
                        .set(DELIVERY.TYPE, code)
                );
            }
            var mergeAllItems = Flux.fromIterable(ofNullable(order.items())
                    .orElse(List.of()).stream().map(item -> dsl.insertInto(ITEMS)
                            .set(ITEMS.ORDER_ID, order.id())
                            .set(ITEMS.ID, item.id())
                            .set(ITEMS.AMOUNT, item.amount())
                            .onDuplicateKeyUpdate()
                            .set(ITEMS.AMOUNT, item.amount())
                    ).map(Mono::from).toList()).flatMap(i1 -> i1).reduce(Integer::sum);

            return mergeOrder.flatMap(count -> {
                log.debug("stored order rows {}", count);
                return mergeDelivery.zipWith(mergeAllItems, (deliveryRows, itemRows) -> {
                    log.debug("stored delivery rows {}", deliveryRows);
                    log.debug("stored item rows {}", itemRows);
                    return order;
                });
            });
        });
    }
}
