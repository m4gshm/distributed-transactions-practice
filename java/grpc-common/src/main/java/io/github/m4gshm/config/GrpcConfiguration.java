package io.github.m4gshm.config;

import io.github.m4gshm.Grpc;
import io.github.m4gshm.GrpcExceptionConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GrpcConfiguration {
    @Bean
    public Grpc grpc(GrpcExceptionConverter grpcExceptionConverter) {
        return new Grpc(grpcExceptionConverter);
    }
}
