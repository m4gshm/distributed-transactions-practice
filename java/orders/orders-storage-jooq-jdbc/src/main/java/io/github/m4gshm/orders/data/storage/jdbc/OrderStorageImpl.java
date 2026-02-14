package io.github.m4gshm.orders.data.storage.jdbc;

import io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.storage.Page;
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
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryByCustomerIdAndStatusIn;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryById;
import static io.github.m4gshm.orders.data.storage.jooq.OrderStorageJooqUtils.selectOrdersJoinDeliveryPaged;
import static io.github.m4gshm.storage.Page.getNum;
import static io.github.m4gshm.storage.Page.getSize;
import static io.github.m4gshm.storage.Page.validatePaging;
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

    @Observed
    @Override
    public List<Order> findAll() {
        return findAll(null);
    }

    @Observed
    @Override
    public List<Order> findAll(Page page) {
        return findAll(page, null);
    }

    @Observed
    @Override
    public List<Order> findAll(Page page, OrderStatus status) {
        var num = getNum(page);
        var size = getSize(page);
        validatePaging(num, size);
        var records = selectOrdersJoinDeliveryPaged(dsl, status, size, num);
        return records.stream().map(record -> toOrder(record, record, List.of())).toList();
    }

    @Observed
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

    @Observed
    @Override
    public Order findById(String id) {
        var selectOrder = selectOrdersJoinDeliveryById(dsl, id);
        var selectItems = selectItemsByOrderId(dsl, id);
        var order = selectOrder.fetchOne();
        var items = selectItems.stream().toList();
        return order != null ? toOrder(order, order, items) : null;
    }

    @Observed
    @Override
    @Transactional
    public Order save(Order order) {
        var batch = mergeOrderFullBatch(dsl, order);
        var insertedRowsCount = Arrays.stream(batch.execute()).sum();
        log.debug("save order stored  rows {}", insertedRowsCount);
        return order;
    }

    @Observed
    @Override
    @Transactional
    public Order saveOrderOnly(Order order) {
        mergeOrder(dsl, order).execute();
        return order;
    }
}
