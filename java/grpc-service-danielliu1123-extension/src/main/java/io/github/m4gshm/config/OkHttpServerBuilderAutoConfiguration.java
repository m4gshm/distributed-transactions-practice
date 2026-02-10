package io.github.m4gshm.config;

import grpcstarter.server.GrpcServerProperties;
import io.grpc.okhttp.OkHttpServerBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import static io.grpc.InsecureServerCredentials.create;

@AutoConfiguration
@ConditionalOnClass(OkHttpServerBuilder.class)
@ConditionalOnProperty(value = "grpc.server.custom.type", havingValue = "okhttp")
public class OkHttpServerBuilderAutoConfiguration {

    @Bean
    public OkHttpServerBuilder okHttpServerBuilder(GrpcServerProperties properties) {
        return OkHttpServerBuilder.forPort(properties.getPort(), create());
    }
}
