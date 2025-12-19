package io.github.m4gshm.orders.data.storage.jooq;

import static io.github.m4gshm.DateTimeUtils.orNow;
import static io.github.m4gshm.orders.data.access.jooq.Tables.DELIVERY;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ITEM;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ORDERS;
import static io.github.m4gshm.storage.jooq.Query.selectAll;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static org.jooq.JoinType.LEFT_OUTER_JOIN;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.access.jooq.tables.records.DeliveryRecord;
import io.github.m4gshm.orders.data.access.jooq.tables.records.ItemRecord;
import io.github.m4gshm.orders.data.access.jooq.tables.records.OrdersRecord;
import io.github.m4gshm.orders.data.model.Order;
import jakarta.annotation.Nonnull;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOnConditionStep;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.impl.DSL;

import java.util.Collection;

@Slf4j
@UtilityClass
public class OrderStorageJooqUtils {

    @Nonnull
    public static InsertOnDuplicateSetMoreStep<ItemRecord> insertItem(DSLContext dsl,
                                                                      Order order,
                                                                      Order.Item item) {
        return dsl.insertInto(ITEM)
                .set(ITEM.ORDER_ID, order.id())
                .set(ITEM.ID, item.id())
                .set(ITEM.AMOUNT, item.amount())
                .onDuplicateKeyUpdate()
                .set(ITEM.AMOUNT, DSL.excluded(ITEM.AMOUNT));
    }

    @Nonnull
    public static InsertOnDuplicateSetMoreStep<DeliveryRecord> mergeDelivery(DSLContext dsl,
                                                                             Order order,
                                                                             Order.Delivery delivery) {
        return dsl.insertInto(DELIVERY)
                .set(DELIVERY.ORDER_ID, order.id())
                .set(DELIVERY.ADDRESS, delivery.address())
                .set(DELIVERY.TYPE, delivery.type())
                .onDuplicateKeyUpdate()
                .set(DELIVERY.ADDRESS, DSL.excluded(DELIVERY.ADDRESS))
                .set(DELIVERY.TYPE, DSL.excluded(DELIVERY.TYPE));
    }

    @Nonnull
    public static InsertOnDuplicateSetMoreStep<OrdersRecord> mergeOrder(DSLContext dsl,
                                                                        Order order,
                                                                        OrderStatus orderStatus) {
        return dsl.insertInto(ORDERS)
                .set(ORDERS.ID, order.id())
                .set(ORDERS.STATUS, orderStatus)
                .set(ORDERS.CREATED_AT, orNow(order.createdAt()))
                .set(ORDERS.CUSTOMER_ID, order.customerId())
                .set(ORDERS.RESERVE_ID, order.reserveId())
                .set(ORDERS.PAYMENT_ID, order.paymentId())
                .set(ORDERS.PAYMENT_TRANSACTION_ID, order.paymentTransactionId())
                .set(ORDERS.RESERVE_TRANSACTION_ID, order.reserveTransactionId())
                .onDuplicateKeyUpdate()
                .set(ORDERS.UPDATED_AT, orNow(order.updatedAt()))
                .set(ORDERS.STATUS, DSL.excluded(ORDERS.STATUS))
                .set(ORDERS.RESERVE_ID, DSL.coalesce(DSL.excluded(ORDERS.RESERVE_ID), ORDERS.RESERVE_ID))
                .set(ORDERS.PAYMENT_ID, DSL.coalesce(DSL.excluded(ORDERS.PAYMENT_ID), ORDERS.PAYMENT_ID));
    }

    @Nonnull
    public static SelectConditionStep<Record> selectItemsByOrderId(String id, DSLContext dsl) {
        return selectAllFrom(dsl, ITEM).where(ITEM.ORDER_ID.eq(id));
    }

    @Nonnull
    public static SelectOnConditionStep<Record> selectOrdersJoinDelivery(DSLContext dsl) {
        return selectAll(dsl, ORDERS, DELIVERY)
                .from(ORDERS)
                .join(DELIVERY, LEFT_OUTER_JOIN)
                .on(DELIVERY.ORDER_ID.eq(ORDERS.ID));
    }

    @Nonnull
    public static SelectConditionStep<Record> selectOrdersJoinDeliveryByCustomerIdAndStatusIn(
                                                                                              String clientId,
                                                                                              Collection<
                                                                                                      OrderStatus> statuses,
                                                                                              DSLContext dsl) {
        return selectOrdersJoinDelivery(dsl).where(ORDERS.CUSTOMER_ID.eq(clientId)
                .and(ORDERS.STATUS.in(statuses)));
    }

    @Nonnull
    public static SelectConditionStep<Record> selectOrdersJoinDeliveryById(String id, DSLContext dsl) {
        return selectOrdersJoinDelivery(dsl).where(ORDERS.ID.eq(id));
    }

}
