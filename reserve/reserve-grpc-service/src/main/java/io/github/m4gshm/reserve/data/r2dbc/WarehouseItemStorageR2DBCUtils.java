package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.storage.ReadStorage.NotFoundException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import reactor.core.publisher.Mono;
import io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem;
import io.github.m4gshm.reserve.data.model.WarehouseItem;

import java.util.Map;

import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.just;
import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.NOT_ENOUGHT_AMOUNT;
import static io.github.m4gshm.reserve.data.WarehouseItemStorage.ReserveItem.Result.Status.RESERVED;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@UtilityClass
public class WarehouseItemStorageR2DBCUtils {
    public static WarehouseItem toWarehouseItem(org.jooq.Record record) {
        return WarehouseItem.builder()
                .id(record.get(WAREHOUSE_ITEM.ID))
                .reserved(record.get(WAREHOUSE_ITEM.RESERVED))
                .amount(record.get(WAREHOUSE_ITEM.AMOUNT))
                .updatedAt(record.get(WAREHOUSE_ITEM.UPDATED_AT))
                .build();
    }

    public static Mono<ReserveItem.Result> updateReserveIfEnough(DSLContext dsl, Record record,
                                                                 Map<String, Integer> reserveAmountPerId) {
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
                ).flatMap(count -> {
                    log.debug("reserve item result count, id:{}, rows:{}", id, count);
                    return count > 0
                            ? just(resultBuilder.status(RESERVED).build())
                            : error(new NotFoundException("zero updated count on reserve item " + id));
                });
            } else {
                log.info("not enough item amount, item {}, need {}", id, -remainder);
                return just(resultBuilder.status(NOT_ENOUGHT_AMOUNT).build());
            }
        }
    }
}
