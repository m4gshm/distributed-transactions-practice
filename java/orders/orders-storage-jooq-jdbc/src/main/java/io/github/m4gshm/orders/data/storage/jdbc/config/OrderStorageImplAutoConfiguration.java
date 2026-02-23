package io.github.m4gshm.orders.data.storage.jdbc.config;

import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.data.storage.jdbc.OrderStorageImpl;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@RequiredArgsConstructor
public class OrderStorageImplAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OrderStorage.class)
    @ConditionalOnBean(DSLContext.class)
    public OrderStorage orderStorage(DSLContext dslContext) {
        return new OrderStorageImpl(dslContext);
    }
}
