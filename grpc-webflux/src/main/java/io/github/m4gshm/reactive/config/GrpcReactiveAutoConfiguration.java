package io.github.m4gshm.reactive.config;

import grpcstarter.server.feature.exceptionhandling.GrpcExceptionResolver;
import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.reactive.GrpcReactiveImpl;
import io.github.m4gshm.reactive.MetadataFactory;
import io.github.m4gshm.reactive.MetadataFactoryImpl;
import io.github.m4gshm.reactive.StatusExtractor;
import io.github.m4gshm.reactive.StatusExtractorImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class GrpcReactiveAutoConfiguration {

    @Bean
    public GrpcReactive grpcCoreSubscriberFactory(MetadataFactory metadataFactory,
                                                  StatusExtractor statusExtractor,
                                                  List<GrpcExceptionResolver> grpcExceptionResolvers) {
        return new GrpcReactiveImpl(metadataFactory, statusExtractor, grpcExceptionResolvers);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataFactory metadataFactory() {
        return new MetadataFactoryImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public StatusExtractor statusExtractor() {
        return new StatusExtractorImpl();
    }
}
