package io.github.m4gshm.grpc.reactive.client;

import io.github.m4gshm.grpc.client.ChannelBuilderFactory;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.socket.SocketChannel;
import lombok.RequiredArgsConstructor;
import reactor.netty.resources.LoopResources;

import java.net.InetSocketAddress;
import java.util.function.Function;

@RequiredArgsConstructor
public class ReactiveNettyChannelBuilderFactory implements Function<InetSocketAddress, NettyChannelBuilder> {
    private final ChannelBuilderFactory<?> channelBuilderFactory;
    private final LoopResources loopResources;

    @Override
    public NettyChannelBuilder apply(InetSocketAddress inetSocketAddress) {
        return ((NettyChannelBuilder) channelBuilderFactory.apply(inetSocketAddress))
                .eventLoopGroup(loopResources.onClient(true))
                .channelType(loopResources.onChannelClass(SocketChannel.class, loopResources.onClient(true)));
    }
}
