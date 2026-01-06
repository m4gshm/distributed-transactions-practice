package io.github.m4gshm.jooq.config;

import io.github.m4gshm.jooq.DefaultReactiveJooqImpl;
import io.github.m4gshm.jooq.ReactiveJooq;
import io.opentelemetry.api.OpenTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Slf4j
@AutoConfiguration(after = { DSLContextAutoConfiguration.class, JooqAutoConfiguration.class })
public class DefaultReactiveJooqAutoConfiguration {

    @Bean
    @ConditionalOnBean(DSLContext.class)
    @ConditionalOnMissingBean(ReactiveJooq.class)
    protected ReactiveJooq reactiveJooq(DSLContext dsl,
                                        JdbcTransactionManager platformTransactionManager,
                                        OpenTelemetry openTelemetry) {
        return new DefaultReactiveJooqImpl(dsl, platformTransactionManager, openTelemetry, null);
    }

}
