package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.WarehouseItem;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
import org.jooq.SelectForUpdateOfStep;
import org.jooq.SelectJoinStep;
import org.jooq.UpdateConditionStep;
import reserve.data.access.jooq.Tables;
import reserve.data.access.jooq.tables.records.WarehouseItemRecord;

import java.util.Collection;

import static io.github.m4gshm.storage.jooq.Query.selectAllFrom;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@UtilityClass
public class WarehouseItemStorageJooqUtils {
    private static SelectJoinStep<Record3<String, Integer, Integer>> selectAmount(DSLContext dsl) {
        return dsl.select(WAREHOUSE_ITEM.ID, WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.RESERVED).from(WAREHOUSE_ITEM);
    }

    public static SelectForUpdateOfStep<Record3<String, Integer, Integer>> selectAmountByItemIdsForNonKeyUpdate(
                                                                                                                DSLContext dsl,
                                                                                                                Collection<
                                                                                                                        String> ids) {
        return selectAmount(dsl)
                .where(WAREHOUSE_ITEM.ID.in(ids))
                .orderBy(WAREHOUSE_ITEM.ID)
                .forNoKeyUpdate();
    }

    public static SelectForUpdateOfStep<Record3<String, Integer, Integer>> selectAmountForUpdate(DSLContext dsl,
                                                                                                 String id) {
        return selectAmount(dsl)
                .where(WAREHOUSE_ITEM.ID.eq(id))
                .orderBy(WAREHOUSE_ITEM.ID)
                .forNoKeyUpdate();
    }

    public static SelectJoinStep<Record> selectItems(DSLContext dsl) {
        return selectAllFrom(dsl, WAREHOUSE_ITEM);
    }

    public static SelectConditionStep<Record> selectItemsByWarehouseId(DSLContext dsl, String id) {
        return selectItems(dsl).where(Tables.WAREHOUSE_ITEM.ID.eq(id));
    }

    public static WarehouseItem toWarehouseItem(org.jooq.Record record) {
        return WarehouseItem.builder()
                .id(record.get(WAREHOUSE_ITEM.ID))
                .reserved(record.get(WAREHOUSE_ITEM.RESERVED))
                .amount(record.get(WAREHOUSE_ITEM.AMOUNT))
                .unitCost(record.get(WAREHOUSE_ITEM.UNIT_COST))
                .updatedAt(record.get(WAREHOUSE_ITEM.UPDATED_AT))
                .build();
    }

    public static UpdateConditionStep<WarehouseItemRecord> updateAmountById(DSLContext dsl,
                                                                            String id,
                                                                            int newTotalAmount) {
        return dsl.update(WAREHOUSE_ITEM)
                .set(WAREHOUSE_ITEM.AMOUNT, newTotalAmount)
                .where(WAREHOUSE_ITEM.ID.eq(id));
    }

    public static UpdateConditionStep<WarehouseItemRecord> updateAmountReservedMinus(DSLContext dsl,
                                                                                     int amountForRelease,
                                                                                     String id) {
        return dsl.update(WAREHOUSE_ITEM)
                .set(WAREHOUSE_ITEM.AMOUNT, WAREHOUSE_ITEM.AMOUNT.minus(amountForRelease))
                .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.minus(amountForRelease))
                .where(WAREHOUSE_ITEM.ID.eq(id));
    }

    public static UpdateConditionStep<WarehouseItemRecord> updateReservedMinusById(DSLContext dsl,
                                                                                   Integer amountForReserve,
                                                                                   String id) {
        return dsl.update(WAREHOUSE_ITEM)
                .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.minus(amountForReserve))
                .where(WAREHOUSE_ITEM.ID.eq(id));
    }

    public static UpdateConditionStep<WarehouseItemRecord> updateReservedPlus(DSLContext dsl,
                                                                              Integer amountForReserve,
                                                                              String id) {
        return dsl.update(WAREHOUSE_ITEM)
                .set(WAREHOUSE_ITEM.RESERVED, WAREHOUSE_ITEM.RESERVED.plus(amountForReserve))
                .where(WAREHOUSE_ITEM.ID.eq(id));
    }

}
