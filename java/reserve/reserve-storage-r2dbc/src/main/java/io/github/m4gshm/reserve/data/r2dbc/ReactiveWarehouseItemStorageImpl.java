package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.reserve.data.InvalidReserveValueException;
import io.github.m4gshm.reserve.data.ReleaseItemException;
import io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils;
import io.github.m4gshm.reserve.data.model.ItemOp;
import io.github.m4gshm.reserve.data.model.WarehouseItem;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Record3;
import org.jooq.ResultQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.reserve.data.WarehouseItemStorageJooqUtils.selectAmountByItemIdsForNonKeyUpdate;
import static io.github.m4gshm.storage.jooq.ReactiveUpdateUtils.checkUpdateCount;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.summingInt;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.just;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactiveWarehouseItemStorageImpl implements ReactiveWarehouseItemStorage {
    @Getter
    private final Class<WarehouseItem> entityClass = WarehouseItem.class;

    ReactiveJooq jooq;

    @Override
    public Mono<List<ItemOp.Result>> cancelReserve(Collection<ItemOp> items) {
        return jooq.inTransaction(dsl -> {
            var amountPerId = items.stream()
                    .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));

            var reserveIds = amountPerId.keySet();

            ResultQuery<Record3<String, Integer, Integer>> source = selectAmountByItemIdsForNonKeyUpdate(dsl,
                    reserveIds);
            return Flux.from(source).flatMap(record -> {
                var id = record.get(WAREHOUSE_ITEM.ID);
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                var amountForReserve = requireNonNull(amountPerId.get(id), "unexpected null amount for item " + id);
                var newReserved = alreadyReserved - amountForReserve;
                var remainder = totalAmount + newReserved;
                var resultBuilder = ItemOp.Result.builder().id(id);
                if (newReserved >= 0) {
                    return Mono.from(WarehouseItemStorageJooqUtils.updateReservedMinusById(dsl, amountForReserve, id))
                            .flatMap(checkUpdateCount("item", id, () -> {
                                return resultBuilder.remainder(remainder)
                                        .build();
                            }));
                } else {
                    log.info("reserved cannot be less tah zero: item [{}], reserved [{}]", id, newReserved);
                    return Mono.error(new InvalidReserveValueException(id, newReserved));
                }
            }).collectList();
        });
    }

    @Override
    public Mono<List<WarehouseItem>> findAll() {
        return jooq.inTransaction(dsl -> Flux.from(WarehouseItemStorageJooqUtils.selectItems(dsl))
                .map(WarehouseItemStorageJooqUtils::toWarehouseItem)
                .collectList());
    }

    @Override
    public Mono<WarehouseItem> findById(String id) {
        return jooq.inTransaction(dsl -> {
            return Mono.from(WarehouseItemStorageJooqUtils.selectItemsByWarehouseId(dsl, id))
                    .map(WarehouseItemStorageJooqUtils::toWarehouseItem);
        });
    }

    @Override
    public Mono<List<ItemOp.Result>> release(Collection<ItemOp> items) {
        return jooq.inTransaction(dsl -> {
            var amountPerId = items.stream()
                    .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));
            var reserveIds = amountPerId.keySet();
            return Flux.from(WarehouseItemStorageJooqUtils.selectAmountByItemIdsForNonKeyUpdate(dsl, reserveIds))
                    .flatMap(record -> {
                        var id = record.get(WAREHOUSE_ITEM.ID);
                        var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                        var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                        int amountForRelease = requireNonNull(amountPerId.get(id),
                                "unexpected null amount for item " + id);
                        var reserved = alreadyReserved - amountForRelease;
                        var newTotalAmount = totalAmount - amountForRelease;
                        if (reserved < 0 || newTotalAmount < 0) {
                            return Mono.error(new ReleaseItemException(id, -reserved, -newTotalAmount));
                        } else {
                            return Mono.from(WarehouseItemStorageJooqUtils.updateAmountReservedMinus(dsl,
                                    amountForRelease,
                                    id))
                                    .flatMap(checkUpdateCount("item",
                                            id,
                                            () -> ItemOp.Result.builder()
                                                    .id(id)
                                                    .remainder(newTotalAmount)
                                                    .build()));
                        }
                    })
                    .collectList();
        });
    }

    @Override
    public Mono<List<ItemOp.ReserveResult>> reserve(Collection<ItemOp> items) {
        return jooq.inTransaction(dsl -> {
            var amountPerId = items.stream()
                    .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));

            var reserveIds = amountPerId.keySet();

            return Flux.from(WarehouseItemStorageJooqUtils.selectAmountByItemIdsForNonKeyUpdate(dsl, reserveIds))
                    .flatMap(record -> {
                        var id = record.get(WAREHOUSE_ITEM.ID);
                        var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                        var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                        var available = totalAmount - alreadyReserved;
                        var amountForReserve = requireNonNull(amountPerId.get(id),
                                "unexpected null amount for item " + id);
                        var remainder = available - amountForReserve;
                        var resultBuilder = ItemOp.ReserveResult.builder().id(id).remainder(remainder);
                        if (remainder >= 0) {
                            return Mono.from(WarehouseItemStorageJooqUtils.updateReservedPlus(dsl,
                                    amountForReserve,
                                    id))
                                    .flatMap(checkUpdateCount("item",
                                            id,
                                            () -> resultBuilder.reserved(true).build()
                                    ));
                        } else {
                            log.info("not enough item amount: item [{}], need [{}]", id, -remainder);
                            return just(resultBuilder.reserved(false).build());
                        }
                    })
                    .collectList();
        });
    }

    @Override
    public Mono<ItemOp.Result> topUp(String id, @Min(1) int amount) {
        return jooq.inTransaction(dsl -> {
            var resultBuilder = ItemOp.Result.builder().id(id);
            return Mono.from(WarehouseItemStorageJooqUtils.selectAmountForUpdate(dsl, id)).flatMap(record -> {
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);
                var newTotalAmount = totalAmount + amount;
                var available = newTotalAmount - alreadyReserved;
                return Mono.from(WarehouseItemStorageJooqUtils.updateAmountById(dsl, id, newTotalAmount))
                        .flatMap(checkUpdateCount("item", id, () -> resultBuilder.remainder(available).build()));
            });
        });
    }

}
