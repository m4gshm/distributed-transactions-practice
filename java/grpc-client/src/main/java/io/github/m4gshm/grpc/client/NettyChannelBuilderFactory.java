package io.github.m4gshm.grpc.client;

import io.grpc.netty.NettyChannelBuilder;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;

@RequiredArgsConstructor
public class NettyChannelBuilderFactory implements ChannelBuilderFactory<NettyChannelBuilder> {

    @Override
    public NettyChannelBuilder apply(InetSocketAddress inetSocketAddress) {
        return NettyChannelBuilder.forAddress(inetSocketAddress);
    }

    @Override
    public Class<NettyChannelBuilder> channelBuilderType() {
        return NettyChannelBuilder.class;
    }
}
