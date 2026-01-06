package io.github.m4gshm.tracing.config;

import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = { GrpcTelemetryAutoConfiguration.class, ObservationAutoConfiguration.class })
public class GrpcServerTracingInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnBean(GrpcTelemetry.class)
    public ServerInterceptor grpcServerTracingInterceptor(GrpcTelemetry grpcTelemetry) {
        return grpcTelemetry.newServerInterceptor();
    }

    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    @ConditionalOnClass(ObservationGrpcServerInterceptor.class)
    public ObservationGrpcServerInterceptor observationGrpcServerInterceptor(ObservationRegistry registry) {
        return new ObservationGrpcServerInterceptor(registry);
    }
}
