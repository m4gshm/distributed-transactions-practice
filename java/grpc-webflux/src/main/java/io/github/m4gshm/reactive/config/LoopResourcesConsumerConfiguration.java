package io.github.m4gshm.reactive.config;

import grpcstarter.server.GrpcServerCustomizer;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.socket.ServerSocketChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ReactorResourceFactory;
import reactor.netty.resources.LoopResources;

import static io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.LOOP_RESOURCES;

@AutoConfiguration(after = LoopResourcesConfiguration.class)
public class LoopResourcesConsumerConfiguration {

    @Bean
    @ConditionalOnBean(LoopResources.class)
    public ConnectionFactoryOptionsBuilderCustomizer connectionFactoryOptionsBuilderCustomizer(
                                                                                               LoopResources sharedLoopResources
    ) {
        return builder -> {
            builder.option(LOOP_RESOURCES, sharedLoopResources);
        };
    }

    @Bean
    @ConditionalOnBean(LoopResources.class)
    GrpcServerCustomizer grpcServerCustomizer(LoopResources sharedLoopResources) {
        return serverBuilder -> {
            if (serverBuilder instanceof NettyServerBuilder nettyServerBuilder) {
                var bossEventLoopGroup = sharedLoopResources.onServerSelect(true);
                var workerEventLoopGroup = sharedLoopResources.onServer(true);
                var channelType = sharedLoopResources.onChannelClass(ServerSocketChannel.class, workerEventLoopGroup);
                nettyServerBuilder.bossEventLoopGroup(bossEventLoopGroup);
                nettyServerBuilder.workerEventLoopGroup(workerEventLoopGroup);
                nettyServerBuilder.channelType(channelType);
                serverBuilder.directExecutor();
            }
        };
    }

    @Bean
    @ConditionalOnBean(LoopResources.class)
    public NettyServerCustomizer nettyServerCustomizer(LoopResources sharedLoopResources) {
        return httpServer -> httpServer.runOn(sharedLoopResources);
    }

    @Bean
    @ConditionalOnBean(LoopResources.class)
    public ReactorResourceFactoryBeanPostProcessor reactorResourceFactoryBeanPostProcessor(
                                                                                           LoopResources sharedLoopResources
    ) {
        return new ReactorResourceFactoryBeanPostProcessor(sharedLoopResources);
    }

    @RequiredArgsConstructor
    public static class ReactorResourceFactoryBeanPostProcessor implements BeanPostProcessor {
        private final LoopResources sharedLoopResources;

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof ReactorResourceFactory reactorResourceFactory) {
                reactorResourceFactory.setUseGlobalResources(false);
                reactorResourceFactory.setLoopResources(sharedLoopResources);
            }
            return bean;
        }
    }

}
