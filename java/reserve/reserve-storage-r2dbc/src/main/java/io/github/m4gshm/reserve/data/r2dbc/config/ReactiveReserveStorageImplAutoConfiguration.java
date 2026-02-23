package io.github.m4gshm.reserve.data.r2dbc.config;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.reserve.data.r2dbc.ReactiveReserveStorage;
import io.github.m4gshm.reserve.data.r2dbc.ReactiveReserveStorageR2dbc;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ReactiveReserveStorageImplAutoConfiguration {

    @Bean
    ReactiveReserveStorage reactiveReserveStorage(ReactiveJooq jooq) {
        return new ReactiveReserveStorageR2dbc(jooq);
    }
}
