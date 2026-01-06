package io.github.m4gshm.grpc.reactive.client;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.socket.SocketChannel;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import reactor.netty.resources.LoopResources;

import java.util.List;

@Slf4j
@UtilityClass
public class NettyChannelBuilderUtils {
    public static NettyChannelBuilder initBuilderByLoopResources(ObjectProvider<LoopResources> loopResources,
                                                                 NettyChannelBuilder builder) {
        var lr = loopResources.getIfAvailable();
        if (lr != null) {
            var eventLoopGroup = lr.onClient(true);
            var channelType = lr.onChannelClass(SocketChannel.class, eventLoopGroup);
            builder.eventLoopGroup(eventLoopGroup)
                    .channelFactory(new ReflectiveChannelFactory<>(channelType));
        } else {
            log.info("LoopResources not provided for grpc client");
        }
        return builder;
    }

    public static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>> newManagedChannelBuilder(
                                                                                                     ClientProperties clientProperties,
                                                                                                     List<ClientInterceptor> interceptors,
                                                                                                     ObjectProvider<
                                                                                                             LoopResources> sharedLoopResources
    ) {
        return initBuilderByLoopResources(sharedLoopResources,
                ClientProperties.newManagedChannelBuilder(clientProperties, interceptors)
        );
    }
}
