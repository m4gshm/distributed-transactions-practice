package io.github.m4gshm.payments.data.r2dbc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.r2dbc.PaymentStorageR2DBC;
import io.github.m4gshm.utils.Jooq;
import io.github.m4gshm.utils.config.R2DBCJooqAutoConfiguration;
import lombok.RequiredArgsConstructor;

@AutoConfiguration(after = R2DBCJooqAutoConfiguration.class)
@RequiredArgsConstructor
@ConditionalOnBean(Jooq.class)
public class PaymentStorageR2DBCAutoConfiguration {
    private final Jooq jooq;

    @Bean
    public PaymentStorage paymentStorage() {
        return new PaymentStorageR2DBC(jooq);
    }
}
