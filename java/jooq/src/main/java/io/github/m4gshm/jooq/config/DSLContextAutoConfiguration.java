package io.github.m4gshm.jooq.config;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class DSLContextAutoConfiguration {
    @Bean
    @ConditionalOnBean(Configuration.class)
    @ConditionalOnMissingBean(DSLContext.class)
    public DSLContext dslContext(Configuration configuration) {
        return DSL.using(configuration);
    }

}
