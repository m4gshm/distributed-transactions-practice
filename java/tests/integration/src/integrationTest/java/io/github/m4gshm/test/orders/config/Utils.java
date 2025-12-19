package io.github.m4gshm.test.orders.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import lombok.experimental.UtilityClass;

import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;

@UtilityClass
public class Utils {
    public static ManagedChannel newManagedChannel(
                                                   ClientProperties clientProperties,
                                                   List<ClientInterceptor> clientInterceptors
    ) {
        return newManagedChannelBuilder(clientProperties, clientInterceptors, null).build();
    }
}
