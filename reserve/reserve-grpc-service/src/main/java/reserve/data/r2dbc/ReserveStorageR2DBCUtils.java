package reserve.data.r2dbc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import reactor.core.publisher.Mono;
import reserve.data.access.jooq.tables.records.ReserveItemRecord;
import reserve.data.model.Reserve;
import reserve.data.model.Reserve.Status;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static jooq.utils.Query.getCurrentTxidOrNull;
import static jooq.utils.Query.selectAllFrom;
import static reactor.core.publisher.Mono.from;
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
                .items(items.stream().map(item -> Reserve.Item.builder()
                        .id(item.get(RESERVE_ITEM.ID))
                        .count(item.get(RESERVE_ITEM.AMOUNT))
                        .build()).toList())
                .build();
    }

    public static Mono<Reserve> storeReserve(DSLContext dsl, Reserve reserve) {
        return Mono.defer(() -> {
            //todo: remove after debug has been ended
            var currentTxid = getCurrentTxidOrNull(dsl, "NOTXID").doOnSuccess(txid -> {
                log.debug("with txid {}", txid);
            });

            var mergeReserve = from(dsl.insertInto(RESERVE)
                    .set(RESERVE.ID, reserve.id())
                    .set(RESERVE.CREATED_AT, orNow(reserve.createdAt()))
                    .set(RESERVE.EXTERNAL_REF, reserve.externalRef())
                    .set(RESERVE.STATUS, reserve.status().getCode())
                    .onDuplicateKeyUpdate()
                    .set(RESERVE.UPDATED_AT, orNow(reserve.updatedAt()))
            ).flatMap(count -> currentTxid.map(id -> count));

            var mergeAllItems = from(dsl.batch(Stream.ofNullable(reserve.items()).flatMap(Collection::stream).map(item1 -> {
                return mergeItem(dsl, reserve, item1);
            }).toList())).flatMap(count -> currentTxid.map(id -> count));

//            var items = ofNullable(reserve.items()).orElse(List.of());
//            var mergeAllItems = fromIterable(items).flatMap(item -> from(mergeItem(dsl, reserve, item)
//            )).collectList().map(countList -> countList.stream().mapToInt(i -> i).reduce(0, Integer::sum));

            return mergeReserve.flatMap(count -> {
                log.debug("stored reserve count {}", count);
                return mergeAllItems.map(itemsCount -> {
                    log.debug("stored items count {}'", itemsCount);
                    return reserve;
                });
            });
        });
    }

    private static InsertOnDuplicateSetMoreStep<ReserveItemRecord> mergeItem(DSLContext dsl, Reserve reserve, Reserve.Item item) {
        return dsl.insertInto(RESERVE_ITEM)
                .set(RESERVE_ITEM.RESERVE_ID, reserve.id())
                .set(RESERVE_ITEM.ID, item.id())
                .set(RESERVE_ITEM.AMOUNT, item.count())
                .onDuplicateKeyUpdate()
                .set(RESERVE_ITEM.AMOUNT, item.count());
    }

    public static SelectJoinStep<Record> selectReserves(DSLContext dsl) {
        return selectAllFrom(dsl, RESERVE);
    }
}
