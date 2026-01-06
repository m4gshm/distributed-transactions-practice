package io.github.m4gshm.orders.data.storage.jdbc.config;

import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.data.storage.jdbc.OrderStorageImpl;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@RequiredArgsConstructor
//@ConditionalOnBean(DSLContext.class)
public class OrderStorageAutoConfiguration {
    private final DSLContext dslContext;

    @Bean
    public OrderStorage orderStorage() {
        return new OrderStorageImpl(dslContext);
    }
}
