package io.github.m4gshm.grpc.client;

import io.grpc.okhttp.OkHttpChannelBuilder;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;

@RequiredArgsConstructor
public class OkHttpChannelBuilderFactory implements ChannelBuilderFactory<OkHttpChannelBuilder> {

    @Override
    public OkHttpChannelBuilder apply(InetSocketAddress inetSocketAddress) {
        return OkHttpChannelBuilder.forAddress(inetSocketAddress.getHostName(), inetSocketAddress.getPort());
    }

    @Override
    public Class<OkHttpChannelBuilder> channelBuilderType() {
        return OkHttpChannelBuilder.class;
    }

}
