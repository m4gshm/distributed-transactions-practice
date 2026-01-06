package io.github.m4gshm.config;

import grpcstarter.server.feature.exceptionhandling.GrpcExceptionResolver;
import io.github.m4gshm.GrpcExceptionConverter;
import io.github.m4gshm.MetadataFactory;
import io.github.m4gshm.StatusExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class GrpcExceptionConverterAutoConfiguration {
    @Bean
    public GrpcExceptionConverter grpcExceptionConverter(MetadataFactory metadataFactory,
                                                         StatusExtractor statusExtractor,
                                                         List<GrpcExceptionResolver> grpcExceptionResolvers) {
        return new GrpcExceptionConverter(metadataFactory, statusExtractor, grpcExceptionResolvers);
    }
}
