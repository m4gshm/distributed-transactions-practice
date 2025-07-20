package reserve.data.r2dbc;

import jooq.utils.TwoPhaseTransaction;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reserve.data.ReserveStorage;
import reserve.data.model.Reserve;

import java.util.List;

import static jooq.utils.Query.selectAllFrom;
import static lombok.AccessLevel.PRIVATE;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static reactor.core.publisher.Mono.from;
import static reserve.data.access.jooq.Tables.ITEMS;
import static reserve.data.access.jooq.Tables.RESERVE;
import static reserve.data.r2dbc.ReserveStorageR2DBCUtils.selectReserves;
import static reserve.data.r2dbc.ReserveStorageR2DBCUtils.storeReserve;
import static reserve.data.r2dbc.ReserveStorageR2DBCUtils.toReserve;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReserveStorageR2DBC implements ReserveStorage {
    DSLContext dsl;

    @Override
    public Mono<List<Reserve>> findAll() {
        return Flux.from(selectReserves(dsl)).map(record -> toReserve(record, List.of())).collectList();
    }

    @Override
    public Mono<Reserve> findById(String id) {
        var selectReserves = from(selectReserves(dsl).where(RESERVE.ID.eq(id)));
        var selectItems = selectAllFrom(dsl, ITEMS).where(ITEMS.RESERVE_ID.eq(id));
        return from(selectReserves).zipWith(Flux.from(selectItems).collectList(), (reserve, items) -> {
            return toReserve(reserve, items);
        });
    }

    @Override
    public Mono<Reserve> save(Reserve reserve, boolean twoPhasedTransaction) {
        return from(dsl.transactionPublisher(trx -> {
            var dsl = trx.dsl();
            var routine = storeReserve(dsl, reserve);
            var id = reserve.id();
            return !twoPhasedTransaction ? routine : TwoPhaseTransaction.prepare(routine, dsl, id);
        }));
    }
}
