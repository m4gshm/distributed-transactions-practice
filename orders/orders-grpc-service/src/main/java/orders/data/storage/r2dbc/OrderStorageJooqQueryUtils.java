package orders.data.storage.r2dbc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import orders.data.model.Order;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

import static java.util.Optional.ofNullable;
import static jooq.utils.Query.selectAll;
import static orders.data.access.jooq.Tables.DELIVERY;
import static orders.data.access.jooq.Tables.ITEMS;
import static orders.data.access.jooq.Tables.ORDERS;
import static org.jooq.JoinType.LEFT_OUTER_JOIN;
import static reactor.core.publisher.Mono.from;

@Slf4j
@UtilityClass
public class OrderStorageJooqQueryUtils {

    public static Mono<Order> storeOrder(DSLContext dsl, Order order) {
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

    public static SelectOnConditionStep<Record> selectOrdersJoinDelivery(DSLContext dsl) {
        return selectAll(dsl, ORDERS, DELIVERY)
                .from(ORDERS)
                .join(DELIVERY, LEFT_OUTER_JOIN).on(DELIVERY.ORDER_ID.eq(ORDERS.ID));
    }

    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }
}
