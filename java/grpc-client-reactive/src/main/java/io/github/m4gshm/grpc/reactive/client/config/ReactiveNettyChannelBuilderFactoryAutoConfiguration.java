package io.github.m4gshm.grpc.reactive.client.config;

import io.github.m4gshm.grpc.client.ChannelBuilderFactory;
import io.github.m4gshm.grpc.reactive.client.ReactiveNettyChannelBuilderFactory;
import io.grpc.netty.NettyChannelBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import reactor.netty.resources.LoopResources;

@AutoConfiguration
public class ReactiveNettyChannelBuilderFactoryAutoConfiguration {
    @Bean
    @ConditionalOnBean(LoopResources.class)
    public BeanPostProcessor reactiveNettyChannelBuilderFactoryPostProcessor(LoopResources loopResources) {
        return new BeanPostProcessor() {
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ChannelBuilderFactory<?> channelBuilderFactory) {
                    var builderType = channelBuilderFactory.channelBuilderType();
                    if (NettyChannelBuilder.class.equals(builderType)) {
                        return new ReactiveNettyChannelBuilderFactory(channelBuilderFactory, loopResources);
                    }
                }
                return bean;
            }
        };
    }
}
