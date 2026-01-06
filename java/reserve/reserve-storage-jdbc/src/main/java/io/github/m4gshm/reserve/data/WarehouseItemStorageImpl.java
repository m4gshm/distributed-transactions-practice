package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.ItemOp;
import io.github.m4gshm.reserve.data.model.WarehouseItem;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.selectAmountByItemIdsForNonKeyUpdate;
import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.selectAmountForUpdate;
import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.selectItems;
import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.selectItemsByWarehouseId;
import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.updateAmountById;
import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.updateAmountReservedMinus;
import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.updateReservedPlus;
import static io.github.m4gshm.storage.UpdateUtils.checkUpdateCount;
import static io.github.m4gshm.storage.UpdateUtils.notFound;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.summingInt;
import static lombok.AccessLevel.PRIVATE;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class WarehouseItemStorageImpl implements WarehouseItemStorage {
    @Getter
    private final Class<WarehouseItem> entityClass = WarehouseItem.class;

    DSLContext dsl;

    private static <T> T checkFound(String id, T item) {
        if (item == null) {
            throw notFound("item", id);
        }
        return item;
    }

    @Override
    public List<ItemOp.Result> cancelReserve(Collection<ItemOp> items) {
        var amountPerId = items.stream()
                .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));

        var reserveIds = amountPerId.keySet();

        return selectAmountByItemIdsForNonKeyUpdate(dsl, reserveIds).fetch().stream().map(record -> {
            var id = record.get(WAREHOUSE_ITEM.ID);
            var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
            var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

            var amountForReserve = requireNonNull(amountPerId.get(id), "unexpected null amount for item " + id);
            var newReserved = alreadyReserved - amountForReserve;
            var remainder = totalAmount + newReserved;
            var resultBuilder = ItemOp.Result.builder().id(id);
            if (newReserved >= 0) {
                var step = WarehouseItemStorageJooqUtils.updateReservedMinusById(dsl, amountForReserve, id).execute();
                return checkUpdateCount(step,
                        "item",
                        id,
                        () -> resultBuilder.remainder(remainder)
                                .build());
            } else {
                log.info("reserved cannot be less tah zero: item [{}], reserved [{}]", id, newReserved);
                throw new InvalidReserveValueException(id, newReserved);
            }
        }).toList();
    }

    @Override
    public List<WarehouseItem> findAll() {
        return selectItems(dsl).stream()
                .map(WarehouseItemStorageJooqUtils::toWarehouseItem)
                .toList();
    }

    @Override
    public WarehouseItem findById(String id) {
        var record = selectItemsByWarehouseId(dsl, id).fetchOne();
        return record != null ? WarehouseItemStorageJooqUtils.toWarehouseItem(record) : null;
    }

    @Override
    public List<ItemOp.Result> release(Collection<ItemOp> items) {
        var amountPerId = items.stream()
                .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));
        var reserveIds = amountPerId.keySet();
        return selectAmountByItemIdsForNonKeyUpdate(dsl, reserveIds).stream().sequential().map(record -> {
            var id = record.get(WAREHOUSE_ITEM.ID);
            var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
            var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

            int amountForRelease = requireNonNull(amountPerId.get(id),
                    "unexpected null amount for item " + id);
            var reserved = alreadyReserved - amountForRelease;
            var newTotalAmount = totalAmount - amountForRelease;
            if (reserved < 0 || newTotalAmount < 0) {
                throw new ReleaseItemException(id, -reserved, -newTotalAmount);
            } else {
                var count = updateAmountReservedMinus(dsl, amountForRelease, id).execute();
                return checkUpdateCount(count,
                        "item",
                        id,
                        () -> ItemOp.Result.builder()
                                .id(id)
                                .remainder(newTotalAmount)
                                .build());
            }
        })
                .toList();
    }

    @Override
    public List<ItemOp.ReserveResult> reserve(Collection<ItemOp> items) {
        var amountPerId = items.stream()
                .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));

        var reserveIds = amountPerId.keySet();

        return selectAmountByItemIdsForNonKeyUpdate(dsl, reserveIds).stream().map(record -> {
            var id = record.get(WAREHOUSE_ITEM.ID);
            var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
            var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

            var available = totalAmount - alreadyReserved;
            var amountForReserve = requireNonNull(amountPerId.get(id),
                    "unexpected null amount for item " + id);
            var remainder = available - amountForReserve;
            var resultBuilder = ItemOp.ReserveResult.builder().id(id).remainder(remainder);
            if (remainder >= 0) {
                var count = updateReservedPlus(dsl, amountForReserve, id).execute();
                return checkUpdateCount(count,
                        "item",
                        id,
                        () -> resultBuilder.reserved(true).build());
            } else {
                log.info("not enough item amount: item [{}], need [{}]", id, -remainder);
                return resultBuilder.reserved(false).build();
            }
        })
                .toList();
    }

    @Override
    public ItemOp.Result topUp(String id, @Min(1) int amount) {
        var resultBuilder = ItemOp.Result.builder().id(id);
        var record = checkFound(id, selectAmountForUpdate(dsl, id).fetchOne());
        var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
        var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);
        var newTotalAmount = totalAmount + amount;
        var available = newTotalAmount - alreadyReserved;
        var count = updateAmountById(dsl, id, newTotalAmount).execute();
        return checkUpdateCount(count, "item", id, () -> resultBuilder.remainder(available).build());
    }
}
