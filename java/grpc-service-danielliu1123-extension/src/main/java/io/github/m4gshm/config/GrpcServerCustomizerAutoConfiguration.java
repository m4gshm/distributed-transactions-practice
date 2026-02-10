package io.github.m4gshm.config;

import grpcstarter.server.GrpcServerCustomizer;
import io.github.m4gshm.GrpcServerProperties;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.NettyRuntime;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(GrpcServerProperties.class)
public class GrpcServerCustomizerAutoConfiguration {
    private static Optional<Long> secondsOf(Duration grpcServerProperties) {
        return ofNullable(grpcServerProperties).map(Duration::toSeconds);
    }

    @Bean
    @SneakyThrows
    GrpcServerCustomizer grpcServerCustomizerExecutor(GrpcServerProperties grpcServerProperties) {
        var virtualThreadFactory = newVirtualThreadFactory("grpc-vt-srv");
        return serverBuilder -> {
            var executor = newThreadPerTaskExecutor(virtualThreadFactory);

            var executorType = grpcServerProperties.executorType();
            if (executorType != null) {
                switch (executorType) {
                    case DIRECT -> serverBuilder.directExecutor();
                    case VIRTUAL_THREAD -> serverBuilder.executor(executor);
                }
            }
            var eventLoopGroupUseVirtualThread = grpcServerProperties.eventLoopGroupUseVirtualThread();
            if (eventLoopGroupUseVirtualThread) {
                if (serverBuilder instanceof NettyServerBuilder nettyServerBuilder) {
                    var epollAvailable = isEpollAvailable() && !grpcServerProperties.eventLoopGroupForceNio();
                    var eventLoopGroupSize = grpcServerProperties.eventLoopGroupSize();
                    var eventLoops = eventLoopGroupSize > 0
                            ? eventLoopGroupSize
                            : NettyRuntime.availableProcessors();
                    var eventLoopGroup = newEventLoopGroup(virtualThreadFactory, eventLoops, epollAvailable);
                    nettyServerBuilder
                            .workerEventLoopGroup(eventLoopGroup)
                            .bossEventLoopGroup(eventLoopGroup)
                            .channelType(epollAvailable ? getEpollServerSocketChannelClass()
                                    : NioServerSocketChannel.class);
                }
                if (serverBuilder instanceof OkHttpServerBuilder okHttpServerBuilder) {
                    okHttpServerBuilder.transportExecutor(executor);
                }
            }
            secondsOf(grpcServerProperties.keepAliveTime()).ifPresent(s -> serverBuilder.keepAliveTime(s, SECONDS));
            secondsOf(grpcServerProperties.keepAliveTimeout()).ifPresent(s -> serverBuilder.keepAliveTimeout(s,
                    SECONDS));
            serverBuilder.permitKeepAliveWithoutCalls(grpcServerProperties.permitKeepAliveWithoutCalls());
            secondsOf(grpcServerProperties.permitKeepAliveTimeInNanos()).ifPresent(s -> serverBuilder
                    .permitKeepAliveTime(s, SECONDS));
        };
    }

}
