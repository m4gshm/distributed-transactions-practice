package io.github.m4gshm.orders.data.storage.jdbc;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.orders.data.access.jooq.Tables.ITEM;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ORDERS;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqMapperUtils.toOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.insertItem;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeDelivery;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectItemsByOrderId;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDelivery;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryByCustomerIdAndStatusIn;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryById;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageImpl implements OrderStorage {
    @Getter
    private final Class<Order> entityClass = Order.class;
    DSLContext dsl;

    @Override
    public List<Order> findAll() {
        return selectOrdersJoinDelivery(dsl).stream().map(record -> toOrder(record, record, List.of())).toList();
    }

    @Override
    public List<Order> findByClientIdAndStatuses(String clientId, Collection<OrderStatus> statuses) {
        var selectOrder = selectOrdersJoinDeliveryByCustomerIdAndStatusIn(dsl, clientId, statuses);
        var orders = selectOrder.stream().toList();
        var orderIds = orders.stream().map(record -> record.get(ORDERS.ID)).toList();

        var selectItems = selectAllFrom(dsl, ITEM).where(ITEM.ORDER_ID.in(orderIds));
        var itemsGroupedByOrderId = selectItems.stream()
                .collect(groupingBy(item -> item.get(ITEM.ORDER_ID)));

        return orders.stream().map(order -> {
            var orderItems = itemsGroupedByOrderId.get(order.get(ORDERS.ID));
            return toOrder(order, order, orderItems);
        }).toList();
    }

    @Override
    public Order findById(String id) {
        var selectOrder = selectOrdersJoinDeliveryById(dsl, id);
        var selectItems = selectItemsByOrderId(dsl, id);
        var order = selectOrder.fetchOne();
        var items = selectItems.stream().toList();
        return order != null ? toOrder(order, order, items) : null;
    }

    @Override
    @Transactional
    public Order save(DSLContext dsl, Order order) {
        mergeOrder(dsl, order).execute();

        var delivery = order.delivery();

        var mergeDelivery = delivery != null
                ? mergeDelivery(dsl, order, delivery).execute()
                : null;

//        log.debug("stored delivery rows {}", mergeDelivery);

        var items = ofNullable(order.items()).orElse(List.of());
        var mergeAllItems = items.stream()
                .map(item -> insertItem(dsl, order, item))
                .map(Query::execute)
                .reduce(Integer::sum)
                .orElse(null);

//        log.debug("stored item rows {}", mergeAllItems);
        return order;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        return save(this.dsl, order);
    }

}
