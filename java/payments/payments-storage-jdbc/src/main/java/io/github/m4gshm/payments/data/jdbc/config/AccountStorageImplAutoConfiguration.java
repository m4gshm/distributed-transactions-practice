package io.github.m4gshm.payments.data.jdbc.config;

import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.jdbc.AccountStorageImpl;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@RequiredArgsConstructor
public class AccountStorageImplAutoConfiguration {
    private final DSLContext dsl;

    @Bean
    public AccountStorage accountStorage() {
        return new AccountStorageImpl(dsl);
    }
}
