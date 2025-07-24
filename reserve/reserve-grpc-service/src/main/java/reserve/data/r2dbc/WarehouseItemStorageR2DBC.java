package reserve.data.r2dbc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reserve.data.WarehouseItemStorage;
import reserve.data.model.WarehouseItem;

import java.util.List;

import static jooq.utils.Query.selectAllFrom;
import static lombok.AccessLevel.PRIVATE;
import static reserve.data.access.jooq.Tables.WAREHOUSE_ITEM;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class WarehouseItemStorageR2DBC implements WarehouseItemStorage {
    @Getter
    private final Class<WarehouseItem> entityClass = WarehouseItem.class;
    private final DSLContext dsl;

    private static WarehouseItem toWarehouseItem(org.jooq.Record record) {
        return WarehouseItem.builder()
                .id(record.get(WAREHOUSE_ITEM.ID))
                .reserved(record.get(WAREHOUSE_ITEM.RESERVED))
                .amount(record.get(WAREHOUSE_ITEM.AMOUNT))
                .updatedAt(record.get(WAREHOUSE_ITEM.UPDATED_AT))
                .build();
    }

    private SelectJoinStep<Record> selectItems() {
        return selectAllFrom(dsl, WAREHOUSE_ITEM);
    }

    @Override
    public Mono<List<WarehouseItem>> findAll() {
        var select = selectItems();
        return Flux.from(select).map(record -> {
            return toWarehouseItem(record);
        }).collectList();
    }

    @Override
    public Mono<WarehouseItem> findById(String id) {
        var select = selectItems().where(WAREHOUSE_ITEM.ID.eq(id));
        return Mono.from(select).map(record -> {
            return toWarehouseItem(record);
        });
    }

}
