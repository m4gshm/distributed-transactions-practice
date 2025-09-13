package io.github.m4gshm.reserve.data.r2dbc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import io.github.m4gshm.reserve.data.model.WarehouseItem;

import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@UtilityClass
public class WarehouseItemStorageR2DBCUtils {
    public static WarehouseItem toWarehouseItem(org.jooq.Record record) {
        return WarehouseItem.builder()
                .id(record.get(WAREHOUSE_ITEM.ID))
                .reserved(record.get(WAREHOUSE_ITEM.RESERVED))
                .amount(record.get(WAREHOUSE_ITEM.AMOUNT))
                .unitCost(record.get(WAREHOUSE_ITEM.UNIT_COST))
                .updatedAt(record.get(WAREHOUSE_ITEM.UPDATED_AT))
                .build();
    }

}
