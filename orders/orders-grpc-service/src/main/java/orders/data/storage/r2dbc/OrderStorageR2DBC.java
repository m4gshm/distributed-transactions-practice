package orders.data.storage.r2dbc;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.data.model.Order;
import orders.data.storage.OrderStorage;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.TableLike;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static orders.data.access.jooq.Tables.DELIVERY;
import static orders.data.access.jooq.Tables.ITEMS;
import static orders.data.access.jooq.Tables.ORDERS;
import static org.jooq.JoinType.LEFT_OUTER_JOIN;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageR2DBC implements OrderStorage {
    DSLContext dslContext;

    private static Order toOrder(Record order, Record delivery, List<Record> items) {
        return Order.builder()
                .id(order.get(ORDERS.ID))
                .createdAt(order.get(ORDERS.CREATED_AT))
                .updatedAt(order.get(ORDERS.UPDATED_AT))
                .customerId(order.get(ORDERS.CUSTOMER_ID))
                .reserveId(order.get(ORDERS.RESERVE_ID))
                .paymentId(order.get(ORDERS.PAYMENT_ID))
                .delivery(toDelivery(delivery))
                .items(items.stream().map(item -> Order.Item.builder()
                        .id(item.get(ITEMS.ID))
                        .name(item.get(ITEMS.NAME))
                        .cost(item.get(ITEMS.COST))
                        .build()).toList())
                .build();
    }

    private static Order.Delivery toDelivery(Record delivery) {
        return delivery == null ? null : Order.Delivery.builder()
                .type(Order.Delivery.Type.byCode(delivery.get(DELIVERY.TYPE)))
                .address(delivery.get(DELIVERY.ADDRESS))
                .build();
    }

    private static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }

    private <R extends Record> SelectJoinStep<Record> selectFrom(TableLike<R> orders) {
        return dslContext.select(orders.fields()).from(orders);
    }

    @Override
    public Mono<List<Order>> findAll() {
        return Flux.from(selectFrom(ORDERS))
                .map(record -> {
                    var order = toOrder(record, null, List.of());
                    return order;
                })
                .collectList();
    }

    @Override
    public Mono<Order> findById(String id) {
        var selectItems = Flux.from(selectFrom(ITEMS).where(ITEMS.ORDER_ID.eq(id))).collectList();
        var selectOrder = Mono.from(selectFrom(ORDERS)
                .join(DELIVERY, LEFT_OUTER_JOIN).on(DELIVERY.ORDER_ID.eq(ORDERS.ID))
                .where(ORDERS.ID.eq(id)));
        return selectOrder.zipWith(selectItems, (order, items) -> toOrder(order, order, items));
    }

    @Override
    public Mono<Order> save(Order order) {

//        ArrayList<Mono<Integer>> list = new ArrayList<>();
        var mergeOrder = Mono.from(dslContext.insertInto(ORDERS)
                .set(ORDERS.ID, order.id())
                .set(ORDERS.CREATED_AT, orNow(order.createdAt()))
                .set(ORDERS.CUSTOMER_ID, order.customerId())
                .set(ORDERS.RESERVE_ID, order.reserveId())
                .set(ORDERS.PAYMENT_ID, order.paymentId())
                .onDuplicateKeyUpdate()
                .set(ORDERS.UPDATED_AT, orNow(order.updatedAt()))
                .set(ORDERS.CUSTOMER_ID, order.customerId())
                .set(ORDERS.RESERVE_ID, order.reserveId())
                .set(ORDERS.PAYMENT_ID, order.paymentId()));
//        list.add(mergeOrder);

        final Mono<Integer> mergeDelivery;
        var delivery = order.delivery();
        if (delivery != null) {
            var code = delivery.type().getCode();
            mergeDelivery = Mono.from(dslContext.insertInto(DELIVERY)
                    .set(DELIVERY.ORDER_ID, order.id())
                    .set(DELIVERY.ADDRESS, delivery.address())
                    .set(DELIVERY.TYPE, code)
                    .onDuplicateKeyUpdate()
                    .set(DELIVERY.ADDRESS, delivery.address())
                    .set(DELIVERY.TYPE, code)
            );

//            list.add(mergeDelivery);
        } else {
            mergeDelivery = Mono.empty();
        }

        var mergeItems = ofNullable(order.items()).orElse(List.of()).stream().map(item -> dslContext.insertInto(ITEMS)
                .set(ITEMS.ORDER_ID, order.id())
                .set(ITEMS.ID, item.id())
                .set(ITEMS.NAME, item.name())
                .set(ITEMS.COST, item.cost())
                .onDuplicateKeyUpdate()
                .set(ITEMS.NAME, item.name())
                .set(ITEMS.COST, item.cost())
        ).map(Mono::from).toList();

        var mergeAllItems = Flux.fromIterable(mergeItems).flatMap(i -> i).reduce(Integer::sum);

//        list.addAll(mergeItems);

        return mergeOrder.flatMap(count -> {
            log.debug("stored order rows {}", count);
            return mergeDelivery.zipWith(mergeAllItems, (d, i) -> {
                log.debug("stored delivery row {}", d);
                log.debug("stored item rows {}", i);
                return order;
            });
        });

//        return Flux.fromIterable(list).flatMap(m -> m).collectList().map(counts -> {
//            long sum = counts.stream().mapToLong(count -> count).sum();
//            log.debug("stored rows {}", sum);
//            return order;
//        }).doOnError(throwable -> {
//            log.error("order store error", throwable);
//        });
    }
}
