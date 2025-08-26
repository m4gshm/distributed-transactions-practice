package io.github.m4gshm.reserve.data.r2dbc;

import static io.github.m4gshm.postgres.prepared.transaction.Transaction.logTxId;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static io.github.m4gshm.storage.jooq.Update.checkUpdateCount;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.summingInt;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.just;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

import java.util.Collection;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;

import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.github.m4gshm.reserve.data.model.WarehouseItem;
import io.github.m4gshm.utils.Jooq;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class WarehouseItemStorageR2DBC implements WarehouseItemStorage {
    @Getter
    private final Class<WarehouseItem> entityClass = WarehouseItem.class;

    private final Jooq jooq;

    private static SelectJoinStep<Record3<String, Integer, Integer>> selectAmount(DSLContext dsl) {
        return dsl.select(WAREHOUSE_ITEM.ID, WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.RESERVED).from(WAREHOUSE_ITEM);
    }

    private static Flux<Record3<String, Integer, Integer>> selectAmountForUpdate(DSLContext dsl,
                                                                                 Collection<String> ids) {
        return Flux.from(selectAmount(dsl)
                .where(WAREHOUSE_ITEM.ID.in(ids))
                .forUpdate());
    }

    private static Mono<Record3<String, Integer, Integer>> selectAmountForUpdate(DSLContext dsl, String id) {
        return Mono.from(selectAmount(dsl)
                .where(WAREHOUSE_ITEM.ID.eq(id))
                .forUpdate());
    }

    private static SelectJoinStep<Record> selectItems(DSLContext dsl) {
        return selectAllFrom(dsl, WAREHOUSE_ITEM);
    }

    @Override
    public Mono<List<ItemOp.Result>> cancelReserve(Collection<ItemOp> items) {
        return jooq.inTransaction(dsl -> {
            var amountPerId = items.stream()
                    .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));

            var reserveIds = amountPerId.keySet();

            return selectAmountForUpdate(dsl, reserveIds).flatMap(record -> {
                var id = record.get(WAREHOUSE_ITEM.ID);
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                var amountForReserve = requireNonNull(amountPerId.get(id), "unexpected null amount for item " + id);
                var newReserved = alreadyReserved - amountForReserve;
                var remainder = totalAmount + newReserved;
                var resultBuilder = ItemOp.Result.builder().id(id);
                if (newReserved >= 0) {
                    return from(dsl.update(WAREHOUSE_ITEM)
                            .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.minus(amountForReserve))
                            .where(WAREHOUSE_ITEM.ID.eq(id))).flatMap(checkUpdateCount("item", id, () -> {
                                return resultBuilder.remainder(remainder)
                                        .build();
                            }));
                } else {
                    log.info("reserved cannot be less tah zero: item [{}], reserved [{}]", id, newReserved);
                    return error(new InvalidReserveValueException(id, newReserved));
                }
            }).collectList().flatMap(l -> logTxId(dsl, "cancelReserve", l));
        });
    }

    @Override
    public Mono<List<WarehouseItem>> findAll() {
        return jooq.inTransaction(dsl -> {
            var select = selectItems(dsl);
            return Flux.from(select).map(WarehouseItemStorageR2DBCUtils::toWarehouseItem).collectList();
        });

    }

    @Override
    public Mono<WarehouseItem> findById(String id) {
        return jooq.inTransaction(dsl -> {
            var select = selectItems(dsl).where(WAREHOUSE_ITEM.ID.eq(id));
            return Mono.from(select).map(WarehouseItemStorageR2DBCUtils::toWarehouseItem);
        });
    }

    @Override
    public Mono<List<ItemOp.Result>> release(Collection<ItemOp> items) {
        var amountPerId = items.stream().collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));
        var reserveIds = amountPerId.keySet();
        return jooq.inTransaction(dsl -> {
            return selectAmountForUpdate(dsl, reserveIds).flatMap(record -> {
                var id = record.get(WAREHOUSE_ITEM.ID);
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                int amountForRelease = requireNonNull(amountPerId.get(id), "unexpected null amount for item " + id);
                var reserved = alreadyReserved - amountForRelease;
                var newTotalAmount = totalAmount - amountForRelease;
                if (reserved < 0 || newTotalAmount < 0) {
                    return error(new ReleaseItemException(id, -reserved, -newTotalAmount));
                } else {
                    return from(dsl.update(WAREHOUSE_ITEM)
                            .set(WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.AMOUNT.minus(amountForRelease))
                            .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.minus(amountForRelease))
                            .where(WAREHOUSE_ITEM.ID.eq(id))).flatMap(checkUpdateCount("item",
                                    id,
                                    () -> {
                                        return ItemOp.Result.builder()
                                                .id(id)
                                                .remainder(newTotalAmount)
                                                .build();
                                    }));
                }
            }).collectList().flatMap(l -> logTxId(dsl, "release", l));
        });
    }

    @Override
    public Mono<List<ItemOp.ReserveResult>> reserve(Collection<ItemOp> items) {
        return jooq.inTransaction(dsl -> {
            var amountPerId = items.stream()
                    .collect(groupingBy(ItemOp::id, mapping(ItemOp::amount, summingInt(i -> i))));

            var reserveIds = amountPerId.keySet();

            return selectAmountForUpdate(dsl, reserveIds).flatMap(record -> {
                var id = record.get(WAREHOUSE_ITEM.ID);
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                var available = totalAmount - alreadyReserved;
                var amountForReserve = requireNonNull(amountPerId.get(id), "unexpected null amount for item " + id);
                var remainder = available - amountForReserve;
                var resultBuilder = ItemOp.ReserveResult.builder().id(id).remainder(remainder);
                if (remainder >= 0) {
                    return from(dsl.update(WAREHOUSE_ITEM)
                            .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.plus(amountForReserve))
                            .where(WAREHOUSE_ITEM.ID.eq(id))).flatMap(checkUpdateCount("item",
                                    id,
                                    () -> resultBuilder.reserved(true).build()
                            ));
                } else {
                    log.info("not enough item amount: item [{}], need [{}]", id, -remainder);
                    return just(resultBuilder.reserved(false).build());
                }
            }).collectList().flatMap(l -> logTxId(dsl, "reserve", l));
        });
    }

    @Override
    public Mono<ItemOp.Result> topUp(String id, @Min(1) int amount) {
        return jooq.inTransaction(dsl -> {
            var resultBuilder = ItemOp.Result.builder().id(id);
            return selectAmountForUpdate(dsl, id).flatMap(record -> {
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);
                var newTotalAmount = totalAmount + amount;
                var available = newTotalAmount - alreadyReserved;
                return from(dsl.update(WAREHOUSE_ITEM)
                        .set(WAREHOUSE_ITEM.AMOUNT, newTotalAmount))
                        .flatMap(checkUpdateCount("item", id, () -> resultBuilder.remainder(available).build()));
            });
        });
    }

}
