package io.github.m4gshm.tracing.config;

import io.github.m4gshm.tracing.TraceService;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import static io.micrometer.observation.ObservationRegistry.NOOP;

@AutoConfiguration
public class TraceServiceAutoConfiguration {
    @Bean
    public TraceService traceService(ObjectProvider<ObservationRegistry> observationRegistry) {
        return new TraceService(observationRegistry.getIfAvailable(() -> NOOP));
    }
}
