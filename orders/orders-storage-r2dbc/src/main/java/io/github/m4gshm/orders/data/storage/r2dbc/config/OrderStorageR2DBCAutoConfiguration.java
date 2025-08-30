package io.github.m4gshm.orders.data.storage.r2dbc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.config.R2DBCJooqAutoConfiguration;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.data.storage.r2dbc.OrderStorageR2DBC;
import lombok.RequiredArgsConstructor;

@AutoConfiguration(after = R2DBCJooqAutoConfiguration.class)
@RequiredArgsConstructor
@ConditionalOnBean(Jooq.class)
public class OrderStorageR2DBCAutoConfiguration {
    private final Jooq jooq;

    @Bean
    public OrderStorage orderStorage() {
        return new OrderStorageR2DBC(jooq);
    }
}
