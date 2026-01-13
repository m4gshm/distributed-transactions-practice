package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.Reserve;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Batch;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import reserve.data.access.jooq.tables.records.ReserveItemRecord;
import reserve.data.access.jooq.tables.records.ReserveRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static io.github.m4gshm.DateTimeUtils.orNow;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static java.util.stream.Stream.ofNullable;
import static reserve.data.access.jooq.Tables.RESERVE;
import static reserve.data.access.jooq.Tables.RESERVE_ITEM;

@Slf4j
@UtilityClass
public class ReserveStorageJooqUtils {

    public static InsertOnDuplicateSetMoreStep<ReserveItemRecord> mergeItem(DSLContext dsl,
                                                                            String reserveId,
                                                                            Reserve.Item item) {
        return dsl.insertInto(RESERVE_ITEM)
                .set(RESERVE_ITEM.RESERVE_ID, reserveId)
                .set(RESERVE_ITEM.ID, item.id())
                .set(RESERVE_ITEM.AMOUNT, item.amount())
                .set(RESERVE_ITEM.RESERVED, item.reserved())
                .onDuplicateKeyUpdate()
                .set(RESERVE_ITEM.AMOUNT, item.amount())
                .set(RESERVE_ITEM.RESERVED, item.reserved());
    }

    public static List<InsertOnDuplicateSetMoreStep<ReserveItemRecord>> mergeItems(DSLContext dsl,
                                                                                   String reserveId,
                                                                                   Collection<Reserve.Item> items) {
        return ofNullable(items).flatMap(Collection::stream).map(item -> {
            return mergeItem(dsl, reserveId, item);
        }).toList();
    }

    public static InsertOnDuplicateSetMoreStep<ReserveRecord> mergeReserve(DSLContext dsl, Reserve reserve) {
        return dsl.insertInto(RESERVE)
                .set(RESERVE.ID, reserve.id())
                .set(RESERVE.CREATED_AT, orNow(reserve.createdAt()))
                .set(RESERVE.EXTERNAL_REF, reserve.externalRef())
                .set(RESERVE.STATUS, reserve.status())
                .onDuplicateKeyUpdate()
                .set(RESERVE.STATUS, DSL.excluded(RESERVE.STATUS))
                .set(RESERVE.UPDATED_AT, orNow(reserve.updatedAt()));
    }

    public static Batch mergeReserveFullBatch(DSLContext dsl, Reserve reserve) {
        return dsl.batch(mergeReserveFullQueries(dsl, reserve));
    }

    public static List<InsertOnDuplicateSetMoreStep<?>> mergeReserveFullQueries(DSLContext dsl, Reserve reserve) {
        var items = mergeItems(dsl, reserve.id(), reserve.items());
        var queries = new ArrayList<InsertOnDuplicateSetMoreStep<?>>(items.size() + 1);
        queries.add(mergeReserve(dsl, reserve));
        queries.addAll(items);
        return queries;
    }

    public static SelectConditionStep<Record> selectItemsByReserveId(DSLContext dsl, String id) {
        return selectAllFrom(dsl, RESERVE_ITEM).where(RESERVE_ITEM.RESERVE_ID.eq(id));
    }

    public static SelectJoinStep<Record> selectReserves(DSLContext dsl) {
        return selectAllFrom(dsl, RESERVE);
    }

    public static SelectConditionStep<Record> selectReservesById(DSLContext dsl, String id) {
        return selectReserves(dsl).where(RESERVE.ID.eq(id));
    }

    public static int sumInt(Collection<Integer> l) {
        return l.stream().filter(Objects::nonNull).mapToInt(i -> i).reduce(0, Integer::sum);
    }

    public static Reserve toReserve(Record record, List<Record> items) {
        return Reserve.builder()
                .id(record.get(RESERVE.ID))
                .externalRef(record.get(RESERVE.EXTERNAL_REF))
                .createdAt(record.get(RESERVE.CREATED_AT))
                .updatedAt(record.get(RESERVE.UPDATED_AT))
                .status(record.get(RESERVE.STATUS))
                .items(items.stream()
                        .map(item -> Reserve.Item.builder()
                                .id(item.get(RESERVE_ITEM.ID))
                                .amount(item.get(RESERVE_ITEM.AMOUNT))
                                .reserved(item.get(RESERVE_ITEM.RESERVED))
                                .build())
                        .toList())
                .build();
    }
}
