package io.github.m4gshm.config;

import io.github.m4gshm.GrpcControllerExceptionHandler;
import io.github.m4gshm.MetadataFactory;
import io.github.m4gshm.StatusExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GrpcControllerExceptionHandlerAutoConfiguration {
    @Bean
    public GrpcControllerExceptionHandler grpcExceptionHandler(MetadataFactory metadataFactory,
                                                               StatusExtractor statusExtractor) {
        return new GrpcControllerExceptionHandler(metadataFactory, statusExtractor);
    }
}
