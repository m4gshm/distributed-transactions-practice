package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.utils.Update;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.github.m4gshm.reserve.data.model.WarehouseItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.github.m4gshm.jooq.utils.Transaction.logTxId;
import static io.github.m4gshm.jooq.utils.Query.selectAllFrom;
import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.insufficient_quantity;
import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.reserved;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.*;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class WarehouseItemStorageR2DBC implements WarehouseItemStorage {
    @Getter
    private final Class<WarehouseItem> entityClass = WarehouseItem.class;
    private final Jooq jooq;

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
    public Mono<Map<String, Double>> getUnitsCost(Collection<String> ids) {
        return fromIterable(ids).flatMap(this::getById).collectList().map(items -> {
            return items.stream().collect(toMap(WarehouseItem::id, WarehouseItem::unitCost));
        });
    }

    @Override
    public Mono<List<ReserveItem.Result>> reserve(Collection<ReserveItem> reserves, String txid) {
        return jooq.transactional(dsl -> {
            var reserveAmountPerId = reserves.stream().collect(groupingBy(ReserveItem::id,
                    mapping(ReserveItem::amount, summingInt(i -> i))));
            var ids = reserveAmountPerId.keySet();
            return Flux.from(dsl
                    .select(WAREHOUSE_ITEM.ID, WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.RESERVED)
                    .from(WAREHOUSE_ITEM)
                    .where(WAREHOUSE_ITEM.ID.in(ids))
                    .forUpdate()
            ).flatMap(record -> {
                var id = record.get(WAREHOUSE_ITEM.ID);
                var amountForReserve = id != null ? reserveAmountPerId.get(id) : null;
                if (amountForReserve == null) {
                    //log
                    return error(new NotFoundException(ReserveItem.class, id));
                } else {
                    var totalAmount = record.get(WAREHOUSE_ITEM.AMOUNT);
                    var alreadyReserved = record.get(WAREHOUSE_ITEM.RESERVED);
                    var available = totalAmount - alreadyReserved;
                    var remainder = available - amountForReserve;
                    var resultBuilder = ReserveItem.Result.builder().id(id).remainder(remainder);
                    if (remainder >= 0) {
                        return from(dsl.update(WAREHOUSE_ITEM)
                                .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.plus(amountForReserve))
                                .where(WAREHOUSE_ITEM.ID.eq(id))
                        ).flatMap(Update.checkUpdate("reserve item", id, () -> resultBuilder.status(reserved).build()));
                    } else {
                        log.info("not enough item amount: item [{}], [need] {}", id, -remainder);
                        return just(resultBuilder.status(insufficient_quantity).build());
                    }
                }
            }).collectList().flatMap(l -> logTxId(dsl, "reserve", l));
        });
    }

}
