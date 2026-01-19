package io.github.m4gshm.orders.data.storage.jooq;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.access.jooq.tables.records.DeliveryRecord;
import io.github.m4gshm.orders.data.access.jooq.tables.records.ItemRecord;
import io.github.m4gshm.orders.data.access.jooq.tables.records.OrdersRecord;
import io.github.m4gshm.orders.data.model.Order;
import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Batch;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.InsertReturningStep;
import org.jooq.JoinType;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOnConditionStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.DateTimeUtils.orNow;
import static io.github.m4gshm.orders.data.access.jooq.Tables.DELIVERY;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ITEM;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ORDERS;
import static io.github.m4gshm.storage.jooq.Query.selectAll;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.Optional.ofNullable;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.excluded;

@Slf4j
@UtilityClass
public class OrderStorageJooqUtils {

    @Nonnull
    public static InsertOnDuplicateSetMoreStep<DeliveryRecord> mergeDelivery(
                                                                             DSLContext dsl,
                                                                             Order order,
                                                                             Order.Delivery delivery) {
        return dsl.insertInto(DELIVERY)
                .set(DELIVERY.ORDER_ID, order.id())
                .set(DELIVERY.ADDRESS, delivery.address())
                .set(DELIVERY.TYPE, delivery.type())
                .onDuplicateKeyUpdate()
                .set(DELIVERY.ADDRESS, excluded(DELIVERY.ADDRESS))
                .set(DELIVERY.TYPE, excluded(DELIVERY.TYPE));
    }

    @Nonnull
    public static InsertOnDuplicateSetMoreStep<ItemRecord> mergeItem(
                                                                     DSLContext dsl,
                                                                     Order order,
                                                                     Order.Item item) {
        return dsl.insertInto(ITEM)
                .set(ITEM.ORDER_ID, order.id())
                .set(ITEM.ID, item.id())
                .set(ITEM.AMOUNT, item.amount())
                .onDuplicateKeyUpdate()
                .set(ITEM.AMOUNT, item.amount());
    }

    @Nonnull
    public static InsertOnDuplicateSetMoreStep<OrdersRecord> mergeOrder(DSLContext dsl, Order order) {
        return dsl.insertInto(ORDERS)
                .set(ORDERS.ID, order.id())
                .set(ORDERS.STATUS, order.status())
                .set(ORDERS.CREATED_AT, orNow(order.createdAt()))
                .set(ORDERS.CUSTOMER_ID, order.customerId())
                .set(ORDERS.RESERVE_ID, order.reserveId())
                .set(ORDERS.PAYMENT_ID, order.paymentId())
                .set(ORDERS.PAYMENT_TRANSACTION_ID, order.paymentTransactionId())
                .set(ORDERS.RESERVE_TRANSACTION_ID, order.reserveTransactionId())
                .onDuplicateKeyUpdate()
                .set(ORDERS.UPDATED_AT, orNow(order.updatedAt()))
                .set(ORDERS.STATUS, order.status())
                .set(ORDERS.PAYMENT_ID, coalesce(excluded(ORDERS.PAYMENT_ID), order.paymentTransactionId()))
                .set(ORDERS.RESERVE_ID, coalesce(excluded(ORDERS.RESERVE_ID), order.reserveTransactionId()));
    }

    public static Batch mergeOrderFullBatch(DSLContext dsl, Order order) {
        return dsl.batch(mergeOrderFullQueries(dsl, order));
    }

    public static List<InsertReturningStep<?>> mergeOrderFullQueries(DSLContext dsl, Order order) {
        var items = ofNullable(order.items()).orElse(List.of());
        var queries = new ArrayList<InsertReturningStep<?>>(2 + items.size());
        queries.add(mergeOrder(dsl, order));

        var delivery = order.delivery();

        if (delivery != null) {
            var mergeDelivery = mergeDelivery(dsl, order, delivery);
            queries.add(mergeDelivery);
        }

        queries.addAll(items.stream().map(item -> mergeItem(dsl, order, item)).toList());
        return queries;
    }

    @Nonnull
    public static SelectConditionStep<Record> selectItemsByOrderId(DSLContext dsl, String id) {
        return selectAllFrom(dsl, ITEM).where(ITEM.ORDER_ID.eq(id));
    }

    @Nonnull
    public static SelectOnConditionStep<Record> selectOrdersJoinDelivery(DSLContext dsl) {
        return selectAll(dsl, ORDERS, DELIVERY)
                .from(ORDERS)
                .join(DELIVERY, JoinType.LEFT_OUTER_JOIN)
                .on(DELIVERY.ORDER_ID.eq(ORDERS.ID));
    }

    @Nonnull
    public static SelectConditionStep<Record> selectOrdersJoinDeliveryByCustomerIdAndStatusIn(
                                                                                              DSLContext dsl,
                                                                                              String clientId,
                                                                                              Collection<
                                                                                                      OrderStatus> statuses) {
        return selectOrdersJoinDelivery(dsl).where(ORDERS.CUSTOMER_ID.eq(clientId)
                .and(ORDERS.STATUS.in(statuses)));
    }

    @Nonnull
    public static SelectConditionStep<Record> selectOrdersJoinDeliveryById(DSLContext dsl, String id) {
        return selectOrdersJoinDelivery(dsl).where(ORDERS.ID.eq(id));
    }
}
