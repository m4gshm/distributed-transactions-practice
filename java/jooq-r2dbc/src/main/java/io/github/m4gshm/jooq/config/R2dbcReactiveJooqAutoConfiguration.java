package io.github.m4gshm.jooq.config;

import io.github.m4gshm.jooq.R2dbcReactiveJooqImpl;
import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.tracing.TraceService;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.ReactiveTransactionManager;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(ConnectionFactory.class)
@FieldDefaults(makeFinal = true, level = PRIVATE)
@AutoConfiguration(after = { R2dbcAutoConfiguration.class,
        R2dbcTransactionManagerAutoConfiguration.class,
        DSLContextAutoConfiguration.class })
public class R2dbcReactiveJooqAutoConfiguration {

    @Bean
    public ReactiveJooq reactiveJooq(ReactiveTransactionManager transactionManager,
                                     ConnectionFactory connectionFactory,
                                     Configuration configuration,
                                     TraceService traceService,
                                     DSLContext dslContext) {
        return new R2dbcReactiveJooqImpl(transactionManager,
                connectionFactory,
                configuration,
                traceService,
                dslContext
        );
    }

}
