package io.github.m4gshm.grpc.client;

import io.grpc.ClientInterceptor;
import io.grpc.netty.NettyChannelBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@Data
@NoArgsConstructor
public class ClientProperties {

    private String address;
    private boolean secure;
    private int idleTimeoutSec = 30;

    public static NettyChannelBuilder newManagedChannelBuilder(
                                                               ClientProperties clientProperties,
                                                               List<ClientInterceptor> interceptors
    ) {
        var address = clientProperties.address;
        var builder = NettyChannelBuilder.forTarget(address);
//        builder = builder.directExecutor();
        try {
            builder.offloadExecutor(new VirtualThreadTaskExecutor(
                    "grpc-offload-" + address
            ));
        } catch (UnsupportedOperationException _) {
            log.info("virtual offloadExecutor not supported for grpc client {}", address);
        }

        if (!clientProperties.isSecure()) {
            builder.usePlaintext();
        }
        return builder
                .idleTimeout(clientProperties.idleTimeoutSec, SECONDS)
                .intercept(interceptors);
    }

}
