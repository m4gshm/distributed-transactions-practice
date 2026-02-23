package io.github.m4gshm.payments.data.jdbc.config;

import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.jdbc.PaymentStorageImpl;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@RequiredArgsConstructor
public class PaymentStorageImplAutoConfiguration {
    private final DSLContext dsl;

    @Bean
    public PaymentStorage reactivePaymentStorage() {
        return new PaymentStorageImpl(dsl);
    }
}
