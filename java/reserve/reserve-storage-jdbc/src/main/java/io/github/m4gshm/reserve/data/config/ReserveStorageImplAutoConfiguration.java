package io.github.m4gshm.reserve.data.config;

import io.github.m4gshm.reserve.data.ReserveStorage;
import io.github.m4gshm.reserve.data.ReserveStorageImpl;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ReserveStorageImplAutoConfiguration {

    @Bean
    ReserveStorage reserveStorage(DSLContext dsl) {
        return new ReserveStorageImpl(dsl);
    }
}
