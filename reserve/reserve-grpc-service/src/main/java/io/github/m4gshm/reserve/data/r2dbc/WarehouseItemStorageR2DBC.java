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
import static io.github.m4gshm.jooq.utils.Update.checkUpdate;
import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.insufficient_quantity;
import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.reserved;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;
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
    public Mono<List<ReserveItem.Result>> reserve(Collection<ReserveItem> items) {
        return jooq.transactional(dsl -> {
            var amountPerId = items.stream().collect(groupingBy(ReserveItem::id,
                    mapping(ReserveItem::amount, summingInt(i -> i))));

            var reserveIds = amountPerId.keySet();

            return selectForUpdate(dsl, reserveIds).flatMap(record -> {
                var id = record.get(WAREHOUSE_ITEM.ID);
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                var available = totalAmount - alreadyReserved;
                var amountForReserve = requireNonNull(amountPerId.get(id), "unexpected null amount for item " + id);
                var remainder = available - amountForReserve;
                var resultBuilder = ReserveItem.Result.builder().id(id).remainder(remainder);
                if (remainder >= 0) {
                    return from(dsl.update(WAREHOUSE_ITEM)
                            .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.plus(amountForReserve))
                            .where(WAREHOUSE_ITEM.ID.eq(id))
                    ).flatMap(checkUpdate("reserve item", id, () -> resultBuilder.status(reserved).build()));
                } else {
                    log.info("not enough item amount: item [{}], [need] {}", id, -remainder);
                    return just(resultBuilder.status(insufficient_quantity).build());
                }
            }).collectList().flatMap(l -> logTxId(dsl, "reserve", l));
        });
    }

    @Override
    public Mono<List<ReleaseItem.Result>> release(Collection<ReleaseItem> items) {
        var amountPerId = items.stream().collect(groupingBy(ReleaseItem::id,
                mapping(ReleaseItem::amount, summingInt(i -> i))));
        var reserveIds = amountPerId.keySet();

        return jooq.transactional(dsl -> {
            return selectForUpdate(dsl, reserveIds).<ReleaseItem.Result>flatMap(record -> {
                var id = record.get(WAREHOUSE_ITEM.ID);
                var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);

                int amountForRelease = requireNonNull(amountPerId.get(id), "unexpected null amount for item " + id);
                var reserved1 = alreadyReserved - amountForRelease;
                var newTotalAmount = totalAmount - amountForRelease;
                if (reserved1 < 0 || newTotalAmount < 0) {
                    return Mono.<ReleaseItem.Result>error(new ReleaseItemException(id, -reserved1, -newTotalAmount));
                } else {
                    return from(dsl.update(WAREHOUSE_ITEM)
                            .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.minus(reserved1))
                            .set(WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.AMOUNT.minus(reserved1))
                            .where(WAREHOUSE_ITEM.ID.eq(id))
                    ).flatMap(checkUpdate("reserve item", id, () -> {
                        return ReleaseItem.Result.builder().id(id).remainder(newTotalAmount).build();
                    }));
                }
            });
        });
    }

}
