package io.github.m4gshm.orders.data.storage.r2dbc;

import java.util.List;

import org.jooq.Record;

import io.github.m4gshm.orders.data.access.jooq.Tables;
import io.github.m4gshm.orders.data.model.Order;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OrderStorageJooqMapperUtils {
    private static Order.Delivery toDelivery(Record delivery) {
        if (delivery == null) {
            return null;
        } else {
            var address = delivery.get(Tables.DELIVERY.ADDRESS);
            return address == null ? null
                    : Order.Delivery.builder()
                            .type(Order.Delivery.Type.byCode(delivery.get(Tables.DELIVERY.TYPE)))
                            .address(address)
                            .build();
        }
    }

    private static Order.Item toItem(Record item) {
        return Order.Item.builder()
                .id(item.get(Tables.ITEMS.ID))
                .amount(item.get(Tables.ITEMS.AMOUNT))
                .build();
    }

    public static Order toOrder(Record order, Record delivery, List<Record> items) {
        return Order.builder()
                .id(order.get(Tables.ORDERS.ID))
                .status(Order.Status.byCode(order.get(Tables.ORDERS.STATUS)))
                .createdAt(order.get(Tables.ORDERS.CREATED_AT))
                .updatedAt(order.get(Tables.ORDERS.UPDATED_AT))
                .customerId(order.get(Tables.ORDERS.CUSTOMER_ID))
                .reserveId(order.get(Tables.ORDERS.RESERVE_ID))
                .paymentId(order.get(Tables.ORDERS.PAYMENT_ID))
                .reserveTransactionId(order.get(Tables.ORDERS.RESERVE_TRANSACTION_ID))
                .paymentTransactionId(order.get(Tables.ORDERS.PAYMENT_TRANSACTION_ID))
                .delivery(toDelivery(delivery))
                .items(items.stream().map(OrderStorageJooqMapperUtils::toItem).toList())
                .build();
    }

}
