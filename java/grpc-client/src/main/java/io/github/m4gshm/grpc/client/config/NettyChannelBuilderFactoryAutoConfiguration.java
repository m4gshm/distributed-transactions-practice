package io.github.m4gshm.grpc.client.config;

import io.github.m4gshm.grpc.client.NettyChannelBuilderFactory;
import io.grpc.netty.NettyChannelBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(NettyChannelBuilder.class)
@ConditionalOnProperty(value = "grpc.client.channel.builder", havingValue = "netty", matchIfMissing = true)
public class NettyChannelBuilderFactoryAutoConfiguration {
    @Bean
    public NettyChannelBuilderFactory nettyChannelBuilderFactory() {
        return new NettyChannelBuilderFactory();
    }
}
