package io.github.m4gshm.payments.data.r2dbc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.jooq.config.R2dbcReactiveJooqAutoConfiguration;
import io.github.m4gshm.payments.data.ReactiveAccountStorage;
import io.github.m4gshm.payments.data.r2dbc.ReactiveAccountStorageR2dbc;
import lombok.RequiredArgsConstructor;

@AutoConfiguration(after = R2dbcReactiveJooqAutoConfiguration.class)
@RequiredArgsConstructor
@ConditionalOnBean(ReactiveJooq.class)
public class ReactiveAccountStorageR2dbcAutoConfiguration {
    private final ReactiveJooq jooq;

    @Bean
    public ReactiveAccountStorage accountStorage() {
        return new ReactiveAccountStorageR2dbc(jooq);
    }
}
