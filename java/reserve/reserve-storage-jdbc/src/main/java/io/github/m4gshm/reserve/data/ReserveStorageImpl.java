package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.Reserve;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.mergeItem;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.mergeReserve;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectItemsByReserveId;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectReserves;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.selectReservesById;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.sumInt;
import static io.github.m4gshm.reserve.data.ReserveStorageJooqUtils.toReserve;
import static java.util.stream.Stream.ofNullable;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Service
@Validated
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
    @Transactional
    public Reserve findById(String id) {
        var selectReserves = selectReservesById(dsl, id).fetchOne();
        if (selectReserves == null) {
            return null;
        }
        return toReserve(selectReserves, selectItemsByReserveId(dsl, id).stream().toList());
    }

    public Integer mergeItems(DSLContext dsl, String reserveId, Collection<Reserve.Item> items) {
        var batch = dsl.batch(ofNullable(items).flatMap(Collection::stream).map(item -> {
            return mergeItem(dsl, reserveId, item);
        }).toList());
        return sumInt(Arrays.stream(batch.execute()).boxed().toList());
    }

    @Override
    @Transactional
    public Reserve save(@Valid Reserve reserve) {
        mergeReserve(reserve, dsl).execute();

        mergeItems(dsl, reserve.id(), reserve.items());
        return reserve;

    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Reserve.Item> saveReservedItems(String reserveId, @Valid Collection<Reserve.Item> items) {
        mergeItems(dsl, reserveId, items);
        return items instanceof List<?> l ? (List<Reserve.Item>) l : items.stream().toList();
    }

}
