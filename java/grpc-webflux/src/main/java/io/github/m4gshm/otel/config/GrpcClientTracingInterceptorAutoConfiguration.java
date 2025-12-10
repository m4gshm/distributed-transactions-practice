package io.github.m4gshm.otel.config;

import io.grpc.ClientInterceptor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = { ObservationAutoConfiguration.class, GrpcTelemetryAutoConfiguration.class })
public class GrpcClientTracingInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnBean(GrpcTelemetry.class)
    public ClientInterceptor grpcClientTracingInterceptor(GrpcTelemetry grpcTelemetry) {
        return grpcTelemetry.newClientInterceptor();
    }
}
