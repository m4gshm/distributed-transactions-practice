package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.Reserve;
import io.micrometer.observation.annotation.Observed;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.mergeReserveFullBatch;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectItemsByReserveId;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectReserves;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectReservesById;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.toReserve;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.transaction.annotation.Propagation.SUPPORTS;

@Slf4j
@Service
@Validated
@Observed
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReserveStorageImpl implements ReserveStorage {
    @Getter
    Class<Reserve> entityClass = Reserve.class;
    DSLContext dsl;

    @Override
    public List<Reserve> findAll() {
        return selectReserves(dsl).stream().map(record -> {
            return toReserve(record, List.of());
        }).toList();
    }

    @Override
    @Transactional(propagation = SUPPORTS)
    public Reserve findById(String id) {
        var selectReserves = selectReservesById(dsl, id).fetchOne();
        if (selectReserves == null) {
            return null;
        }
        return toReserve(selectReserves, selectItemsByReserveId(dsl, id).stream().toList());
    }

    @Override
    @Transactional
    public Reserve save(@Valid Reserve reserve) {
        mergeReserveFullBatch(dsl, reserve).execute();
        return reserve;

    }

}
