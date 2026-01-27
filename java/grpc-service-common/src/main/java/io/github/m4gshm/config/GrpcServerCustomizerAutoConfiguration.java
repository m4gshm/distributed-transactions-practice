package io.github.m4gshm.config;

import grpcstarter.server.GrpcServerCustomizer;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.Optional;

import static io.github.m4gshm.EventLoopGroupUtils.getEpollServerSocketChannelClass;
import static io.github.m4gshm.EventLoopGroupUtils.isEpollAvailable;
import static io.github.m4gshm.EventLoopGroupUtils.newEventLoopGroup;
import static io.github.m4gshm.EventLoopGroupUtils.newVirtualThreadFactory;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

@AutoConfiguration
@EnableConfigurationProperties(GrpcServerCustomizerAutoConfiguration.GrpcServerProperties.class)
public class GrpcServerCustomizerAutoConfiguration {
    private static Optional<Long> secondsOf(Duration grpcServerProperties) {
        return ofNullable(grpcServerProperties).map(Duration::toSeconds);
    }

    @Bean
    @SneakyThrows
    GrpcServerCustomizer grpcServerCustomizerExecutor(GrpcServerProperties grpcServerProperties) {
        var threadFactory = newVirtualThreadFactory("grpc-vt-srv");
        return serverBuilder -> {
            var executorType = grpcServerProperties.executorType;
            if (executorType != null) {
                switch (executorType) {
                    case DIRECT -> serverBuilder.directExecutor();
                    case VIRTUAL_THREAD -> {
                        serverBuilder.executor(newThreadPerTaskExecutor(threadFactory));
//                        if (serverBuilder instanceof NettyServerBuilder nettyServerBuilder) {
//                            var epollAvailable = isEpollAvailable();
//                            var eventLoopGroup = newVirtualThreadEventLoopGroup("grpc-vt-srv-worker",
//                                    availableProcessors(), epollAvailable);
//                            nettyServerBuilder
//                                    .workerEventLoopGroup(eventLoopGroup)
//                                    .bossEventLoopGroup(eventLoopGroup)
//                                    .channelType(epollAvailable ? getEpollServerSocketChannelClass()
//                                            : NioServerSocketChannel.class);
//                        }
                    }
                }
            }
            if (serverBuilder instanceof NettyServerBuilder nettyServerBuilder) {
                var epollAvailable = isEpollAvailable();
                var eventLoopGroup = newEventLoopGroup(threadFactory, 1, epollAvailable);
                nettyServerBuilder
                        .workerEventLoopGroup(eventLoopGroup)
                        .bossEventLoopGroup(eventLoopGroup)
                        .channelType(epollAvailable ? getEpollServerSocketChannelClass()
                                : NioServerSocketChannel.class);
            }
//            if (executorType != VIRTUAL_THREAD && grpcServerProperties.eventLoopGroupSize > 0) {
//                if (serverBuilder instanceof NettyServerBuilder nettyServerBuilder) {
//                    var epollAvailable = isEpollAvailable();
//                    var workeredEventLoopGroup = newEventLoopGroup(new DefaultThreadFactory("grpc-ovrd-worker"),
//                            grpcServerProperties.eventLoopGroupSize,
//                            epollAvailable);
//                    nettyServerBuilder
//                            .bossEventLoopGroup(workeredEventLoopGroup)
//                            .workerEventLoopGroup(workeredEventLoopGroup)
//                            .channelType(epollAvailable ? getEpollServerSocketChannelClass()
//                                    : NioServerSocketChannel.class);
//                }
//            }
            secondsOf(grpcServerProperties.keepAliveTime()).ifPresent(s -> serverBuilder.keepAliveTime(s, SECONDS));
            secondsOf(grpcServerProperties.keepAliveTimeout()).ifPresent(s -> serverBuilder.keepAliveTimeout(s,
                    SECONDS));
            serverBuilder.permitKeepAliveWithoutCalls(grpcServerProperties.permitKeepAliveWithoutCalls());
            secondsOf(grpcServerProperties.permitKeepAliveTimeInNanos()).ifPresent(s -> serverBuilder
                    .permitKeepAliveTime(s, SECONDS));
        };
    }

    @ConfigurationProperties("grpc.server")
    public record GrpcServerProperties(@DefaultValue("VIRTUAL_THREAD") ExecutorType executorType,
                                       @DefaultValue("1m") Duration keepAliveTime,
                                       @DefaultValue("5s") Duration keepAliveTimeout,
                                       @DefaultValue("true") boolean permitKeepAliveWithoutCalls,
                                       @DefaultValue("1m") Duration permitKeepAliveTimeInNanos,
                                       @DefaultValue("2") int eventLoopGroupSize
    ) {
        public enum ExecutorType {
                DIRECT, VIRTUAL_THREAD, UNDEFINED;
        }
    }
}
