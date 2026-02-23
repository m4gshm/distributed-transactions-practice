package io.github.m4gshm.tracing.config;

import io.micrometer.tracing.exporter.SpanExportingPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ActuatorDropSpanExportingPredicateAutoConfiguration {
    // micrometer impl
    @Bean
    @ConditionalOnClass(SpanExportingPredicate.class)
    SpanExportingPredicate actuatorDropSpanExportingPredicate() {
        return span -> {
            return !span.getName().contains("/actuator");
        };
    }
}
