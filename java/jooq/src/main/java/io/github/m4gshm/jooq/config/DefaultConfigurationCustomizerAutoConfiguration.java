package io.github.m4gshm.jooq.config;

import org.jooq.conf.Settings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = JooqAutoConfiguration.class)
public class DefaultConfigurationCustomizerAutoConfiguration {
    // @Bean
//    DefaultConfigurationCustomizer configurationCustomizers() {
//        return new DefaultConfigurationCustomizer() {
//            @Override
//            public void customize(DefaultConfiguration configuration) {
//                configuration.
//            }
//        };
//    }
//
    @Bean
    public Settings settings() {
        return new Settings()
                .withCacheParsingConnection(true)
                .withCachePreparedStatementInLoader(true)
                .withCacheRecordMappers(true)
                .withReflectionCaching(true)
                .withRenderSchema(false)
                .withRenderCatalog(false)
                .withBindOffsetDateTimeType(true)
                .withBindOffsetTimeType(true)
                ;
    }
}
