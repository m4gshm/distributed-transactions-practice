package io.github.m4gshm.grpc.client.config;

import io.github.m4gshm.grpc.client.OkHttpChannelBuilderFactory;
import io.grpc.okhttp.OkHttpChannelBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OkHttpChannelBuilder.class)
@ConditionalOnProperty(value = "grpc.client.channel.builder", havingValue = "okhttp")
public class OkHttpChannelBuilderFactoryAutoConfiguration {
    @Bean
    public OkHttpChannelBuilderFactory okHttpChannelBuilderFactory() {
        return new OkHttpChannelBuilderFactory();
    }
}
