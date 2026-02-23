package io.github.m4gshm.orders.data.storage.r2dbc.config;

import io.github.m4gshm.tracing.TraceService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.jooq.config.R2dbcReactiveJooqAutoConfiguration;
import io.github.m4gshm.orders.data.storage.ReactiveOrderStorage;
import io.github.m4gshm.orders.data.storage.r2dbc.ReactiveOrderStorageR2dbc;
import lombok.RequiredArgsConstructor;

@AutoConfiguration(after = R2dbcReactiveJooqAutoConfiguration.class)
@RequiredArgsConstructor
@ConditionalOnBean(ReactiveJooq.class)
public class ReactiveOrderStorageR2DBCAutoConfiguration {
    private final ReactiveJooq jooq;

    @Bean
    @ConditionalOnMissingBean(ReactiveOrderStorage.class)
    public ReactiveOrderStorage reactiveOrderStorage(TraceService traceService) {
        return new ReactiveOrderStorageR2dbc(jooq, traceService);
    }
}
