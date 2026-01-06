package io.github.m4gshm.orders.data.storage.jdbc.config;

import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.data.storage.ReactiveOrderStorage;
import io.github.m4gshm.orders.data.storage.jdbc.ReactiveOrderStorageJdbcWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = OrderStorageAutoConfiguration.class)
@RequiredArgsConstructor
public class ReactiveOrderStorageWrapperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveOrderStorage.class)
    public ReactiveOrderStorage reactiveOrderStorage(OrderStorage orderStorage) {
        return new ReactiveOrderStorageJdbcWrapper(orderStorage);
    }
}
