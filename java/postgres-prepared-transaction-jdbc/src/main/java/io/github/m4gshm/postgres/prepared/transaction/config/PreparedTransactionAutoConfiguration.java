package io.github.m4gshm.postgres.prepared.transaction.config;

import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionServiceImpl;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@RequiredArgsConstructor
public class PreparedTransactionAutoConfiguration {

    private final DSLContext dslContext;

    @Bean
    public PreparedTransactionService preparedTransactionService() {
        return new PreparedTransactionServiceImpl(dslContext);
    }
}
