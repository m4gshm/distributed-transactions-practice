package io.github.m4gshm.config;

import io.github.m4gshm.Grpc;
import io.github.m4gshm.GrpcExceptionConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GrpcAutoConfiguration {
    @Bean
    public Grpc grpc(ObjectProvider<GrpcExceptionConverter> grpcExceptionConverter) {
        return new Grpc(grpcExceptionConverter.getIfAvailable(() -> {
            return throwable -> throwable;
        }));
    }
}
