package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.github.m4gshm.reserve.data.model.WarehouseItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.jooq.utils.Query.selectAllFrom;
import static io.github.m4gshm.jooq.utils.Transaction.logTxId;
import static io.github.m4gshm.jooq.utils.Update.checkUpdateCount;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.just;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class WarehouseItemStorageR2DBC implements WarehouseItemStorage {
    @Getter
    private final Class<WarehouseItem> entityClass = WarehouseItem.class;
    private final Jooq jooq;

    private static Flux<Record3<String, Integer, Integer>> selectForUpdate(DSLContext dsl, Collection<String> ids) {
        return Flux.from(dsl
                .select(WAREHOUSE_ITEM.ID, WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.RESERVED)
                .from(WAREHOUSE_ITEM)
                .where(WAREHOUSE_ITEM.ID.in(ids))
                .forUpdate()
        );
    }

    private SelectJoinStep<Record> selectItems(DSLContext dsl) {
        return selectAllFrom(dsl, WAREHOUSE_ITEM);
    }

    @Override
    public Mono<List<WarehouseItem>> findAll() {
        return jooq.transactional(dsl -> {
            var select = selectItems(dsl);
            return Flux.from(select).map(WarehouseItemStorageR2DBCUtils::toWarehouseItem).collectList();
        });

    }

    @Override
    public Mono<WarehouseItem> findById(String id) {
        return jooq.transactional(dsl -> {
            var select = selectItems(dsl).where(WAREHOUSE_ITEM.ID.eq(id));
            return Mono.from(select).map(WarehouseItemStorageR2DBCUtils::toWarehouseItem);
        });
    }

    @Override
    public Mono<List<ItemOp.ReserveResult>> reserve(Collection<ItemOp> items) {
        return jooq.transactional(dsl -> {
            var amountPerId = items.stream().collect(groupingBy(ItemOp::id,
                    mapping(ItemOp::amount, summingInt(i -> i))));

            var reserveIds = amountPerId.keySet();

            return selectForUpdate(dsl, reserveIds).flatMap(record -> {
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
                            .where(WAREHOUSE_ITEM.ID.eq(id))
                    ).flatMap(checkUpdateCount("reserve item", id, () -> {
                        return resultBuilder.reserved(true).build();
                    }));
                } else {
                    log.info("not enough item amount: item [{}], need [{}]", id, -remainder);
                    return just(resultBuilder.reserved(false).build());
                }
            }).collectList().flatMap(l -> logTxId(dsl, "reserve", l));
        });
    }

    @Override
    public Mono<List<ItemOp.Result>> cancelReserve(Collection<ItemOp> items) {
        return jooq.transactional(dsl -> {
            var amountPerId = items.stream().collect(groupingBy(ItemOp::id,
                    mapping(ItemOp::amount, summingInt(i -> i))));

            var reserveIds = amountPerId.keySet();

            return selectForUpdate(dsl, reserveIds).flatMap(record -> {
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
                            .where(WAREHOUSE_ITEM.ID.eq(id))
                    ).flatMap(checkUpdateCount("reserve item", id, () -> {
                        return resultBuilder.remainder(remainder).build();
                    }));
                } else {
                    log.info("reserved cannot be less tah zero: item [{}], reserved [{}]", id,newReserved);
                    return error(new InvalidReserveValueException(id, newReserved));
                }
            }).collectList().flatMap(l -> logTxId(dsl, "cancelReserve", l));
        });
    }

    @Override
    public Mono<List<ItemOp.Result>> release(Collection<ItemOp> items) {
        var amountPerId = items.stream().collect(groupingBy(ItemOp::id,
                mapping(ItemOp::amount, summingInt(i -> i))));
        var reserveIds = amountPerId.keySet();
        return jooq.transactional(dsl -> {
            return selectForUpdate(dsl, reserveIds).flatMap(record -> {
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
                            .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.minus(amountForRelease))
                            .set(WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.AMOUNT.minus(amountForRelease))
                            .where(WAREHOUSE_ITEM.ID.eq(id))
                    ).flatMap(checkUpdateCount("reserve item", id, () -> {
                        return ItemOp.Result.builder().id(id).remainder(newTotalAmount).build();
                    }));
                }
            }).collectList().flatMap(l -> logTxId(dsl, "release", l));
        });
    }

}
