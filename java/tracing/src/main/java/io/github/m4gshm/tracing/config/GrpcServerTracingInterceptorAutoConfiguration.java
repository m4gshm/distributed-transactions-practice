package io.github.m4gshm.tracing.config;

import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.context.annotation.Bean;

@ConditionalOnProperty(value = "micrometer.grpc.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfiguration(
        after = { GrpcTelemetryAutoConfiguration.class, ObservationAutoConfiguration.class },
        afterName = "org.springframework.boot.grpc.server.autoconfigure.GrpcServerObservationAutoConfiguration"
)
public class GrpcServerTracingInterceptorAutoConfiguration {

//    @Bean
//    @ConditionalOnBean(GrpcTelemetry.class)
//    public ServerInterceptor grpcServerTracingInterceptor(GrpcTelemetry grpcTelemetry) {
//        return grpcTelemetry.newServerInterceptor();
//    }

    @Bean
    @ConditionalOnBean(ObservationRegistry.class)
    @ConditionalOnClass(ObservationGrpcServerInterceptor.class)
    @ConditionalOnMissingBean(ObservationGrpcServerInterceptor.class)
    public ObservationGrpcServerInterceptor observationGrpcServerInterceptor(ObservationRegistry registry) {
        return new ObservationGrpcServerInterceptor(registry);
    }
}
