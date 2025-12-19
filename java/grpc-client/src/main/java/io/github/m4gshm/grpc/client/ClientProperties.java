package io.github.m4gshm.grpc.client;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@Data
@NoArgsConstructor
public class ClientProperties {

    public static final Class<? extends Channel> EPOLL_CLIENT_CHANNEL_TYPE = loadClassOrThrowException(
            "io.netty.channel.epoll.EpollSocketChannel"
    );
    public static final String EPOLL_CLIENT_ELG_TYPE_NAME = "io.netty.channel.epoll.EpollEventLoopGroup";

    private String address;
    private boolean secure;
    private int idleTimeoutSec = 30;

    private static Class<? extends Channel> getChannelType(EventLoopGroup eventLoopGroup,
                                                           ClientProperties clientProperties) {
        var elg = eventLoopGroup;
        if (eventLoopGroup instanceof Supplier<?> supplier) {
            var object = supplier.get();
            if (object instanceof EventLoopGroup suppliedElg) {
                elg = suppliedElg;
            }
        }
        var channelType = EPOLL_CLIENT_ELG_TYPE_NAME.equals(elg.getClass().getName())
                ? EPOLL_CLIENT_CHANNEL_TYPE
                : NioSocketChannel.class;
        log.info("use channelType {} for client {}", channelType, clientProperties.getAddress());
        return channelType;
    }

    @Nonnull
    private static Class<? extends Channel> loadClassOrThrowException(String className) {
        try {
            return Class.forName(className).asSubclass(Channel.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load " + className, e);
        }
    }

    public static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>> newManagedChannelBuilder(
                                                                                                     ClientProperties clientProperties,
                                                                                                     List<ClientInterceptor> clientInterceptors,
                                                                                                     EventLoopGroup eventLoopGroup) {
        var builder = NettyChannelBuilder.forTarget(clientProperties.getAddress());
        if (eventLoopGroup != null) {
            builder = builder.eventLoopGroup(eventLoopGroup);
            var channelType = getChannelType(eventLoopGroup, clientProperties);
            builder = builder.channelFactory(new ReflectiveChannelFactory<>(channelType));
        }
        builder = builder.directExecutor();
        if (!clientProperties.isSecure()) {
            builder = builder.usePlaintext();
        }
        return builder
                .idleTimeout(clientProperties.idleTimeoutSec, SECONDS)
                .intercept(clientInterceptors);
    }

}
