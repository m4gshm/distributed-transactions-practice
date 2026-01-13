package io.github.m4gshm.orders.data.storage.jdbc;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.micrometer.observation.annotation.Observed;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.orders.data.access.jooq.Tables.ITEM;
import static io.github.m4gshm.orders.data.access.jooq.Tables.ORDERS;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqMapperUtils.toOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeOrder;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.mergeOrderFullBatch;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectItemsByOrderId;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDelivery;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryByCustomerIdAndStatusIn;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryById;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.stream.Collectors.groupingBy;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Validated
@Observed
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageImpl implements OrderStorage {
    @Getter
    private final Class<Order> entityClass = Order.class;
    DSLContext dsl;

    @Override
    public List<Order> findAll() {
        return findAll(null);
    }

    @Override
    public List<Order> findAll(Page page) {
        return findAll(page, null);
    }

    @Override
    public List<Order> findAll(Page page, OrderStatus status) {
        var num = page != null ? page.num() : null;
        var hasNum = num != null;
        if (hasNum && num < 0) {
            throw new IllegalArgumentException("page.num cannot be less than 0");
        }
        var size = page != null ? page.size() : 10;
        if (size <= 0) {
            throw new IllegalArgumentException("page.size must be more than 0");
        }
        var baseQuery = selectOrdersJoinDelivery(dsl);
        var queryWithCondition = status != null ? baseQuery.where(ORDERS.STATUS.eq(status)) : baseQuery;
        return (hasNum
                ? queryWithCondition.limit(size).offset(num * size)
                : queryWithCondition).stream().map(record -> toOrder(record, record, List.of())).toList();
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
    public Order save(Order order) {
        var batch = mergeOrderFullBatch(dsl, order);
        var insertedRowsCount = Arrays.stream(batch.execute()).sum();
        log.debug("save order stored  rows {}", insertedRowsCount);
        return order;
    }

    @Override
    public Order saveOrderOnly(Order order) {
        mergeOrder(dsl, order).execute();
        return order;
    }
}
