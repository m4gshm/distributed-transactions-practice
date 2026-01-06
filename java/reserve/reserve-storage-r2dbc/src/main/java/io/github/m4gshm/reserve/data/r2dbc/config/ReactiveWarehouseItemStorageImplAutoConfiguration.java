package io.github.m4gshm.reserve.data.r2dbc.config;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.reserve.data.r2dbc.ReactiveWarehouseItemStorage;
import io.github.m4gshm.reserve.data.r2dbc.ReactiveWarehouseItemStorageImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ReactiveWarehouseItemStorageImplAutoConfiguration {
    @Bean
    ReactiveWarehouseItemStorage reactiveWarehouseItemStorage(ReactiveJooq jooq) {
        return new ReactiveWarehouseItemStorageImpl(jooq);
    }
}
