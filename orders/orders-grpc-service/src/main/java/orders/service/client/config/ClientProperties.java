package orders.service.client.config;

import io.grpc.ManagedChannelBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.grpc.ManagedChannelBuilder.forTarget;

@Data
@NoArgsConstructor
public class ClientProperties {
    private String address;
    private boolean secure;

    public static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>> newManagedChannelBuilder(
            ClientProperties paymentsClientProperties
    ) {
        var builder = forTarget(paymentsClientProperties.getAddress());
        if (!paymentsClientProperties.isSecure()) {
            builder.usePlaintext();
        }
        return builder;
    }
}
