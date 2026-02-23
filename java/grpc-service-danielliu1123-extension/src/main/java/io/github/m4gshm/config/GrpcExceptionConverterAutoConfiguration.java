package io.github.m4gshm.config;

import grpcstarter.server.feature.exceptionhandling.GrpcExceptionResolver;
import io.github.m4gshm.GrpcExceptionConverter;
import io.github.m4gshm.GrpcExceptionConverterImpl;
import io.github.m4gshm.MetadataFactory;
import io.github.m4gshm.StatusExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class GrpcExceptionConverterAutoConfiguration {
    @Bean
    @ConditionalOnClass(grpcstarter.server.feature.exceptionhandling.GrpcExceptionResolver.class)
    public GrpcExceptionConverter grpcExceptionConverter(MetadataFactory metadataFactory,
                                                         StatusExtractor statusExtractor,
                                                         List<GrpcExceptionResolver> grpcExceptionResolvers) {
        return new GrpcExceptionConverterImpl(metadataFactory, statusExtractor, grpcExceptionResolvers);
    }
}
