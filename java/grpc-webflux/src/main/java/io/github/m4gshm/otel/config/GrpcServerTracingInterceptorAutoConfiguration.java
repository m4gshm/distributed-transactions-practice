package io.github.m4gshm.otel.config;

import io.grpc.ServerInterceptor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = { GrpcTelemetryAutoConfiguration.class })
public class GrpcServerTracingInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnBean(GrpcTelemetry.class)
    public ServerInterceptor grpcServerTracingInterceptor(GrpcTelemetry grpcTelemetry) {
        return grpcTelemetry.newServerInterceptor();
    }
}
