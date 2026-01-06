package io.github.m4gshm.reserve.data.config;

import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.github.m4gshm.reserve.data.WarehouseItemStorageImpl;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class WarehouseItemStorageImplAutoConfiguration {
    @Bean
    WarehouseItemStorage warehouseItemStorage(DSLContext dsl) {
        return new WarehouseItemStorageImpl(dsl);
    }
}
