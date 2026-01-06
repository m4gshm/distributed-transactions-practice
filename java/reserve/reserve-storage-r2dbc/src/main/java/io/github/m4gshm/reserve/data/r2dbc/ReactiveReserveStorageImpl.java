package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.reserve.data.ReserveStorageJooqUtils;
import io.github.m4gshm.reserve.data.model.Reserve;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.mergeItem;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.toReserve;
import static java.util.stream.Stream.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.defer;

@Slf4j
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactiveReserveStorageImpl implements ReactiveReserveStorage {
    @Getter
    Class<Reserve> entityClass = Reserve.class;
    ReactiveJooq jooq;

    @Override
    public Mono<List<Reserve>> findAll() {
        return jooq.inTransaction(dsl -> {
            return Flux.from(ReserveStorageJooqUtils.selectReserves(dsl)).map(record -> {
                return toReserve(record, List.of());
            }).collectList();
        });
    }

    @Override
    public Mono<Reserve> findById(String id) {
        return jooq.inTransaction(dsl -> {
            var selectReserves = Mono.from(ReserveStorageJooqUtils.selectReservesById(dsl, id));
            var selectItems = Flux.from(ReserveStorageJooqUtils.selectItemsByReserveId(dsl, id)).collectList();
            return selectReserves.zipWith(selectItems, ReserveStorageJooqUtils::toReserve);
        });
    }

    public Mono<Integer> mergeItems(DSLContext dsl, String reserveId, Collection<Reserve.Item> items) {
        var batch = dsl.batch(ofNullable(items).flatMap(Collection::stream).map(item -> {
            return mergeItem(dsl, reserveId, item);
        }).toList());
        return Flux.from(batch)
//                .flatMap(count -> logTxId(dsl, "mergeItems", count))
                .collectList()
                .map(ReserveStorageJooqUtils::sumInt)
//                .doOnSuccess(count -> {
//                    log.debug("stored items count {}, reserveId {}", count, reserveId);
//                })
        ;
    }

    @Override
    public Mono<Reserve> save(@Valid Reserve reserve) {
        return jooq.inTransaction(dsl -> defer(() -> {
            var mergeReserve = Mono.from(ReserveStorageJooqUtils.mergeReserve(reserve, dsl))
//                    .flatMap(count -> logTxId(dsl, "mergeReserve", count))
//                    .doOnSuccess(count -> {
//                        log.debug("stored reserve count {}", count);
//                    })
            ;

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
