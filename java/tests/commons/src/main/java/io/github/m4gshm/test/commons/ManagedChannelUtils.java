package io.github.m4gshm.test.commons;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.github.m4gshm.grpc.client.NettyChannelBuilderFactory;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class ManagedChannelUtils {
    public static ManagedChannel newManagedChannel(
                                                   ClientProperties clientProperties,
                                                   List<ClientInterceptor> clientInterceptors
    ) {
        return clientProperties.newManagedChannelBuilder(new NettyChannelBuilderFactory(), clientInterceptors).build();
    }
}
