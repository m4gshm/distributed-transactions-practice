package io.github.m4gshm.grpc.client;

import io.grpc.ManagedChannelBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClientProperties {
    private String address;
    private boolean secure;

    public static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>>
           newManagedChannelBuilder(ClientProperties clientProperties) {
        var builder = ManagedChannelBuilder.forTarget(clientProperties.getAddress());
        if (!clientProperties.isSecure()) {
            builder.usePlaintext();
        }
        return builder;
    }
}
