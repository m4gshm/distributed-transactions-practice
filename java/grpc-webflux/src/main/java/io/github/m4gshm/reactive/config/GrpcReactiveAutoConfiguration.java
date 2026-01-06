package io.github.m4gshm.reactive.config;

import io.github.m4gshm.GrpcExceptionConverter;
import io.github.m4gshm.MetadataFactory;
import io.github.m4gshm.MetadataFactoryImpl;
import io.github.m4gshm.StatusExtractor;
import io.github.m4gshm.StatusExtractorImpl;
import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.reactive.GrpcReactiveImpl;
import io.github.m4gshm.tracing.TraceService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GrpcReactiveAutoConfiguration {

    @Bean
    public GrpcReactive grpcCoreSubscriberFactory(GrpcExceptionConverter grpcExceptionConverter,
                                                  TraceService traceService) {
        return new GrpcReactiveImpl(grpcExceptionConverter, traceService);
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
