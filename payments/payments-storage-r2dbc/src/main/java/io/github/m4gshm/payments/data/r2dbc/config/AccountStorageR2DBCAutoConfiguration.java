package io.github.m4gshm.payments.data.r2dbc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.r2dbc.AccountStorageR2DBC;
import io.github.m4gshm.utils.Jooq;
import io.github.m4gshm.utils.config.R2DBCJooqAutoConfiguration;
import lombok.RequiredArgsConstructor;

@AutoConfiguration(after = R2DBCJooqAutoConfiguration.class)
@RequiredArgsConstructor
@ConditionalOnBean(Jooq.class)
public class AccountStorageR2DBCAutoConfiguration {
    private final Jooq jooq;

    @Bean
    public AccountStorage accountStorage() {
        return new AccountStorageR2DBC(jooq);
    }
}
