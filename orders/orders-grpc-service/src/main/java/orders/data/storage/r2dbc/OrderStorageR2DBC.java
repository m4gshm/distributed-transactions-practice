package orders.data.storage.r2dbc;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.data.model.Order;
import orders.data.storage.OrderStorage;
import org.jooq.DSLContext;
import org.jooq.Fields;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.SelectSelectStep;
import org.jooq.TableLike;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static orders.data.access.jooq.Tables.DELIVERY;
import static orders.data.access.jooq.Tables.ITEMS;
import static orders.data.access.jooq.Tables.ORDERS;
import static orders.data.model.Order.Delivery.Type;
import static orders.data.model.Order.Delivery.builder;
import static org.jooq.JoinType.LEFT_OUTER_JOIN;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageR2DBC implements OrderStorage {
    DSLContext dsl;

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
        if (delivery == null) {
            return null;
        } else {
            var address = delivery.get(DELIVERY.ADDRESS);
            return address == null ? null : builder()
                    .type(Type.byCode(delivery.get(DELIVERY.TYPE)))
                    .address(address)
                    .build();
        }
    }

    private static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }

    private <R extends Record> SelectJoinStep<Record> selectAllFrom(TableLike<R> table) {
        return dsl.select(table.fields()).from(table);
    }

    private SelectSelectStep<Record> selectAll(TableLike<? extends Record> table, TableLike<? extends Record>... tables) {
        var fields = Stream.concat(Stream.of(table), Arrays.stream(tables)).map(Fields::fields).flatMap(Arrays::stream).toList();
        return dsl.select(fields);
    }

    @Override
    public Mono<List<Order>> findAll() {
        return Flux.from(selectOrdersJoinDelivery())
                .map(record -> toOrder(record, record, List.of()))
                .collectList();
    }

    @Override
    public Mono<Order> findById(String id) {
        var selectOrder = selectOrdersJoinDelivery().where(ORDERS.ID.eq(id));
        var selectItems = selectAllFrom(ITEMS).where(ITEMS.ORDER_ID.eq(id));

        return from(selectOrder).zipWith(Flux.from(selectItems).collectList(), (order, items) -> {
            return toOrder(order, order, items);
        });
    }

    private SelectOnConditionStep<Record> selectOrdersJoinDelivery() {
        return selectAll(ORDERS, DELIVERY)
                .from(ORDERS)
                .join(DELIVERY, LEFT_OUTER_JOIN).on(DELIVERY.ORDER_ID.eq(ORDERS.ID));
    }

    @Override
    public Mono<Order> save(Order order, boolean twoPhasedTransaction) {
        return from(dsl.transactionPublisher(trx -> {
            var dsl = trx.dsl();
            var routine = storeOrderFullRoutine(dsl, order);
            return !twoPhasedTransaction ? routine : TwoPhaseTransaction.prepare(routine, dsl, order.id());
        }));
    }

    private Mono<Order> storeOrderFullRoutine(DSLContext dsl, Order order) {
        var mergeOrder = from(dsl.insertInto(ORDERS)
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
                        .set(ITEMS.NAME, item.name())
                        .set(ITEMS.COST, item.cost())
                        .onDuplicateKeyUpdate()
                        .set(ITEMS.NAME, item.name())
                        .set(ITEMS.COST, item.cost())
                ).map(Mono::from).toList()).flatMap(i1 -> i1).reduce(Integer::sum);

        return mergeOrder.flatMap(count -> {
            log.debug("stored order rows {}", count);
            return mergeDelivery.zipWith(mergeAllItems, (d, i) -> {
                log.debug("stored delivery row {}", d);
                log.debug("stored item rows {}", i);
                return order;
            });
        });
    }
}
