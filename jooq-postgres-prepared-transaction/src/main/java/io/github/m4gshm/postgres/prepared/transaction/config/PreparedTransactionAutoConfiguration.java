package io.github.m4gshm.postgres.prepared.transaction.config;

import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionServiceImpl;
import io.github.m4gshm.utils.Jooq;
import io.github.m4gshm.utils.config.R2DBCJooqAutoConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

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
