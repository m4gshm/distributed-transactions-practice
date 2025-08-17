package io.github.m4gshm.orders.service.client;

import static io.grpc.ManagedChannelBuilder.forTarget;

import io.grpc.ManagedChannelBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClientProperties {
    private String address;

    private boolean secure;

    public static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>>
           newManagedChannelBuilder(ClientProperties paymentsClientProperties) {
        var builder = forTarget(paymentsClientProperties.getAddress());
        if (!paymentsClientProperties.isSecure()) {
            builder.usePlaintext();
        }
        return builder;
    }
}
