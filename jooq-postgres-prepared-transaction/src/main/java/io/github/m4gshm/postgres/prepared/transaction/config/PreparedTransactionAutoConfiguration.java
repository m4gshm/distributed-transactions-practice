package io.github.m4gshm.postgres.prepared.transaction.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.config.R2DBCJooqAutoConfiguration;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionServiceImpl;
import lombok.RequiredArgsConstructor;

@ConditionalOnBean(Jooq.class)
@RequiredArgsConstructor
@AutoConfiguration(after = R2DBCJooqAutoConfiguration.class)
public class PreparedTransactionAutoConfiguration {

    private final Jooq jooq;

    @Bean
    public PreparedTransactionService preparedTransactionService() {
        return new PreparedTransactionServiceImpl(jooq);
    }
}
