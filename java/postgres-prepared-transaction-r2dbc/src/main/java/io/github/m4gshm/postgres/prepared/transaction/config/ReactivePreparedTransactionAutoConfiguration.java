package io.github.m4gshm.postgres.prepared.transaction.config;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.jooq.config.R2dbcReactiveJooqAutoConfiguration;
import io.github.m4gshm.postgres.prepared.transaction.ReactivePreparedTransactionService;
import io.github.m4gshm.postgres.prepared.transaction.ReactivePreparedTransactionServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@ConditionalOnBean(ReactiveJooq.class)
@RequiredArgsConstructor
@AutoConfiguration(after = R2dbcReactiveJooqAutoConfiguration.class)
public class ReactivePreparedTransactionAutoConfiguration {

    private final ReactiveJooq jooq;

    @Bean
    public ReactivePreparedTransactionService reactivePreparedTransactionService() {
        return new ReactivePreparedTransactionServiceImpl(jooq);
    }
}
