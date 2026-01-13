package io.github.m4gshm.grpc.client;

import io.grpc.ClientInterceptor;
import io.grpc.netty.NettyChannelBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.ExecutorType.VIRTUAL_THREAD;
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
    private ExecutorType executorType = VIRTUAL_THREAD;

    @SneakyThrows
    public static NettyChannelBuilder newManagedChannelBuilder(
                                                               ClientProperties clientProperties,
                                                               List<ClientInterceptor> interceptors
    ) {
        var address = clientProperties.address;
        var parts = address.split(":");
        var host = parts[0];
        int port = Integer.parseInt(parts[1]);
        var addr = Inet4Address.getByName(host);
        var builder = NettyChannelBuilder.forAddress(new InetSocketAddress(addr, port));
        try {
            builder.offloadExecutor(new VirtualThreadTaskExecutor("grpc-vt-offload-" + address));
        } catch (UnsupportedOperationException _) {
            log.info("virtual offloadExecutor not supported for grpc client {}", address);
        }

        switch (clientProperties.executorType) {
            case ExecutorType.DIRECT -> builder.directExecutor();
            case ExecutorType.VIRTUAL_THREAD -> builder.executor(new VirtualThreadTaskExecutor("grpc-vt-" + address));
        }
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

    public enum ExecutorType {
            DIRECT, VIRTUAL_THREAD;
    }
}
