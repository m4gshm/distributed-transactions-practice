package io.github.m4gshm.orders.data.storage.jooq;

import java.util.List;

import org.jooq.Record;

import io.github.m4gshm.orders.data.model.Order;
import lombok.experimental.UtilityClass;

import static io.github.m4gshm.orders.data.access.jooq.Tables.DELIVERY;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ITEM;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ORDERS;

@UtilityClass
public class OrderStorageJooqMapperUtils {

    private static Order.Delivery toDelivery(Record delivery) {
        if (delivery == null) {
            return null;
        } else {
            var address = delivery.get(DELIVERY.ADDRESS);
            return address == null ? null
                    : Order.Delivery.builder()
                            .type(delivery.get(DELIVERY.TYPE))
                            .address(address)
                            .build();
        }
    }

    private static Order.Item toItem(Record item) {
        return Order.Item.builder()
                .id(item.get(ITEM.ID))
                .amount(item.get(ITEM.AMOUNT))
                .build();
    }

    public static Order toOrder(Record order, Record delivery, List<Record> items) {
        return Order.builder()
                .id(order.get(ORDERS.ID))
                .status(order.get(ORDERS.STATUS))
                .createdAt(order.get(ORDERS.CREATED_AT))
                .updatedAt(order.get(ORDERS.UPDATED_AT))
                .customerId(order.get(ORDERS.CUSTOMER_ID))
                .reserveId(order.get(ORDERS.RESERVE_ID))
                .paymentId(order.get(ORDERS.PAYMENT_ID))
                .reserveTransactionId(order.get(ORDERS.RESERVE_TRANSACTION_ID))
                .paymentTransactionId(order.get(ORDERS.PAYMENT_TRANSACTION_ID))
                .delivery(toDelivery(delivery))
                .items(items.stream().map(OrderStorageJooqMapperUtils::toItem).toList())
                .build();
    }

}
