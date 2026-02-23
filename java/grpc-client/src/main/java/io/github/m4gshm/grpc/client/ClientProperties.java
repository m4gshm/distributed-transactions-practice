package io.github.m4gshm.grpc.client;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@Data
@NoArgsConstructor
public class ClientProperties {
    private String address;
    private boolean secure;
    private long idleTimeoutSec = MINUTES.toSeconds(5);
    private long keepAliveSec = MINUTES.toSeconds(20);
    private ExecutorType executorType = ExecutorType.VIRTUAL_THREAD;

    @SneakyThrows
    public ManagedChannelBuilder<?> newManagedChannelBuilder(
                                                             ChannelBuilderFactory<?> channelBuilderFactory,
                                                             List<ClientInterceptor> interceptors
    ) {
        var clientProperties = this;
        var address = clientProperties.address;
        var executorType = clientProperties.executorType;

        var builder = ManagedChanelBuilderUtils.newManagedChannelBuilder(channelBuilderFactory, address, executorType);

        if (!clientProperties.isSecure()) {
            builder.usePlaintext();
        }
        var keepAliveSec = clientProperties.keepAliveSec;
        if (keepAliveSec >= 0) {
            builder.keepAliveTime(keepAliveSec, SECONDS);
            builder.keepAliveWithoutCalls(true);
        }
        var idleTimeoutSec = clientProperties.idleTimeoutSec;
        if (idleTimeoutSec >= 0) {
            builder.idleTimeout(idleTimeoutSec, SECONDS);
        }
        return builder.intercept(interceptors);
    }

}
