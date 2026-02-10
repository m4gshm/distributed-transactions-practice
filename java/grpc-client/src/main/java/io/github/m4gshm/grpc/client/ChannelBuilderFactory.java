package io.github.m4gshm.grpc.client;

import io.grpc.ManagedChannelBuilder;

import java.net.InetSocketAddress;
import java.util.function.Function;

public interface ChannelBuilderFactory<T extends ManagedChannelBuilder<?>> extends Function<InetSocketAddress, T> {
    Class<T> channelBuilderType();
}
