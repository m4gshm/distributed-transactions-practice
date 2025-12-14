package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.reserve.data.ReserveStorage;
import io.github.m4gshm.reserve.data.model.Reserve;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.reserve.data.r2dbc.ReserveStorageR2DBCUtils.mergeItems;
import static io.github.m4gshm.DateTimeUtils.orNow;
import static io.github.m4gshm.reserve.data.r2dbc.ReserveStorageR2DBCUtils.selectReserves;
import static io.github.m4gshm.reserve.data.r2dbc.ReserveStorageR2DBCUtils.toReserve;
import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.from;
import static reserve.data.access.jooq.Tables.RESERVE;
import static reserve.data.access.jooq.Tables.RESERVE_ITEM;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReserveStorageR2DBC implements ReserveStorage {
    @Getter
    Class<Reserve> entityClass = Reserve.class;
    Jooq jooq;

    @Override
    public Mono<List<Reserve>> findAll() {
        return jooq.inTransaction(dsl -> {
            return Flux.from(selectReserves(dsl)).map(record -> toReserve(record, List.of())).collectList();
        });
    }

    @Override
    public Mono<Reserve> findById(String id) {
        return jooq.inTransaction(dsl -> {
            var selectReserves = from(selectReserves(dsl).where(RESERVE.ID.eq(id)));
            var selectItems = selectAllFrom(dsl, RESERVE_ITEM).where(RESERVE_ITEM.RESERVE_ID.eq(id));
            return from(selectReserves).zipWith(Flux.from(selectItems).collectList(), (reserve, items) -> {
                return toReserve(reserve, items);
            });
        });
    }

    @Override
    public Mono<Reserve> save(@Valid Reserve reserve) {
        return jooq.inTransaction(dsl -> defer(() -> {
            var mergeReserve = from(dsl.insertInto(RESERVE)
                    .set(RESERVE.ID, reserve.id())
                    .set(RESERVE.CREATED_AT, orNow(reserve.createdAt()))
                    .set(RESERVE.EXTERNAL_REF, reserve.externalRef())
                    .set(RESERVE.STATUS, reserve.status())
                    .onDuplicateKeyUpdate()
                    .set(RESERVE.STATUS, DSL.excluded(RESERVE.STATUS))
                    .set(RESERVE.UPDATED_AT, orNow(reserve.updatedAt())))
//                    .flatMap(count -> logTxId(dsl, "mergeReserve", count))
                    .doOnSuccess(count -> {
                        log.debug("stored reserve count {}", count);
                    });

            var mergeAllItems = mergeItems(dsl, reserve.id(), reserve.items());

            return mergeReserve.flatMap(count -> {
                return mergeAllItems.map(itemsCount -> reserve);
            });
        }));
    }

    @Override
    public Mono<List<Reserve.Item>> saveReservedItems(String reserveId, @Valid Collection<Reserve.Item> items) {
        return jooq.inTransaction(dsl -> mergeItems(dsl, reserveId, items)
                        .map(c -> List.copyOf(items))
//                .flatMap(l -> logTxId(dsl, "saveReservedItems", l))
        );
    }
}
