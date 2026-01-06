package io.github.m4gshm.tracing.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@ConditionalOnClass(GrpcTelemetry.class)
@AutoConfiguration(afterName = "io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration")
public class GrpcTelemetryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(GrpcTelemetry.class)
    @ConditionalOnBean(OpenTelemetry.class)
    public GrpcTelemetry grpcTelemetry(OpenTelemetry openTelemetry) {
        return GrpcTelemetry.create(openTelemetry);
    }
}
