package io.github.m4gshm.reserve.data.r2dbc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reserve.data.access.jooq.tables.records.ReserveItemRecord;
import io.github.m4gshm.reserve.data.model.Reserve;
import io.github.m4gshm.reserve.data.model.Reserve.Status;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static io.github.m4gshm.jooq.utils.Transaction.logTxId;
import static io.github.m4gshm.jooq.utils.Query.selectAllFrom;
import static reserve.data.access.jooq.Tables.RESERVE;
import static reserve.data.access.jooq.Tables.RESERVE_ITEM;

@Slf4j
@UtilityClass
public class ReserveStorageR2DBCUtils {

    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }

    public static Reserve toReserve(Record record, List<Record> items) {
        return Reserve.builder()
                      .id(record.get(RESERVE.ID))
                      .externalRef(record.get(RESERVE.EXTERNAL_REF))
                      .createdAt(record.get(RESERVE.CREATED_AT))
                      .updatedAt(record.get(RESERVE.UPDATED_AT))
                      .status(Status.byCode(record.get(RESERVE.STATUS)))
                      .items(items.stream()
                                  .map(item -> Reserve.Item.builder()
                                                           .id(item.get(RESERVE_ITEM.ID))
                                                           .amount(item.get(RESERVE_ITEM.AMOUNT))
                                                           .reserved(item.get(RESERVE_ITEM.RESERVED))
                                                           .build())
                                  .toList())
                      .build();
    }

    public static Mono<Integer> mergeItems(DSLContext dsl, String reserveId, Collection<Reserve.Item> items) {
        return Flux.from(dsl.batch(Stream.ofNullable(items).flatMap(Collection::stream).map(item -> {
            return mergeItem(dsl, reserveId, item);
        }).toList()))
                   .flatMap(count -> logTxId(dsl, "mergeItems", count))
                   .collectList()
                   .map(l -> sumInt(l))
                   .doOnSuccess(count -> {
                       log.debug("stored items count {}, reserveId {}", count, reserveId);
                   });
    }

    private static int sumInt(Collection<Integer> l) {
        return l.stream().filter(Objects::nonNull).mapToInt(i -> i).reduce(0, Integer::sum);
    }

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

    public static SelectJoinStep<Record> selectReserves(DSLContext dsl) {
        return selectAllFrom(dsl, RESERVE);
    }
}
