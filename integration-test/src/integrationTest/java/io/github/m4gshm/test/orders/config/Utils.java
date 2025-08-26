package io.github.m4gshm.test.orders.config;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ManagedChannel;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {
    public static ManagedChannel newManagedChannel(ClientProperties clientProperties) {
        return newManagedChannelBuilder(clientProperties).build();
    }
}
