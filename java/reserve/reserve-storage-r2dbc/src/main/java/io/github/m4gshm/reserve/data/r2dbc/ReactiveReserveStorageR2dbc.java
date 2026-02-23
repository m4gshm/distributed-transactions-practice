package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.reserve.data.ReserveStorageJooqUtils;
import io.github.m4gshm.reserve.data.model.Reserve;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.mergeReserveFullBatch;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectItemsByReserveId;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectReserves;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectReservesById;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.toReserve;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.defer;

@Slf4j
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactiveReserveStorageR2dbc implements ReactiveReserveStorage {
    @Getter
    Class<Reserve> entityClass = Reserve.class;
    ReactiveJooq jooq;

    private static String getOp(String op) {
        return Reserve.class.getSimpleName() + ":" + op;
    }

    @Override
    public Mono<List<Reserve>> findAll() {
        return jooq.supportTransaction(getOp("findAll"), dsl -> {
            return Flux.from(selectReserves(dsl)).map(record -> {
                return toReserve(record, List.of());
            }).collectList();
        });
    }

    @Override
    public Mono<Reserve> findById(String id) {
        return jooq.supportTransaction(getOp("findById"), dsl -> {
            var selectReserves = Mono.from(selectReservesById(dsl, id));
            var selectItems = Flux.from(selectItemsByReserveId(dsl, id)).collectList();
            return selectReserves.zipWith(selectItems, ReserveStorageJooqUtils::toReserve);
        });
    }

    @Override
    public Mono<Reserve> save(@Valid Reserve reserve) {
        return jooq.supportTransaction(getOp("save"), dsl -> defer(() -> {
            Mono<Void> merge;
//            merge = Flux.concat(mergeReserveFullQueries(dsl, reserve).stream().map(Mono::from).toList()).then();
            merge = Flux.from(mergeReserveFullBatch(dsl, reserve)).then();
            return merge.thenReturn(reserve);
        }));
    }

}
