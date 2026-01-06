package io.github.m4gshm.tracing.config;

import io.grpc.ClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = { GrpcTelemetryAutoConfiguration.class, ObservationAutoConfiguration.class })
public class GrpcClientTracingInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnBean({ GrpcTelemetry.class, ClientInterceptor.class })
    public ClientInterceptor grpcClientTracingInterceptor(GrpcTelemetry grpcTelemetry) {
        return grpcTelemetry.newClientInterceptor();
    }

    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    @ConditionalOnClass(ObservationGrpcServerInterceptor.class)
    public ObservationGrpcClientInterceptor observationGrpcClientInterceptor(ObservationRegistry registry) {
        return new ObservationGrpcClientInterceptor(registry);
    }
}
