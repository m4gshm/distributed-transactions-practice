package io.github.m4gshm.reactive.config;

import io.github.m4gshm.reactive.DefaultGrpcExceptionHandler;
import io.github.m4gshm.reactive.MetadataFactory;
import io.github.m4gshm.reactive.StatusExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class DefaultGrpcExceptionHandlerAutoConfiguration {
    @Bean
    public DefaultGrpcExceptionHandler grpcExceptionHandler(MetadataFactory metadataFactory,
            StatusExtractor statusExtractor) {
        return new DefaultGrpcExceptionHandler(metadataFactory, statusExtractor);
    }
}
