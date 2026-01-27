package io.github.m4gshm.grpc.client;

import io.grpc.ClientInterceptor;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.List;

import static io.github.m4gshm.EventLoopGroupUtils.getEpollSocketChannelClass;
import static io.github.m4gshm.EventLoopGroupUtils.isEpollAvailable;
import static io.github.m4gshm.EventLoopGroupUtils.newVirtualThreadEventLoopGroup;
import static io.netty.util.NettyRuntime.availableProcessors;
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

        var executorType = clientProperties.executorType;
        if (executorType != null) {
            switch (executorType) {
                case DIRECT -> builder.directExecutor();
                case VIRTUAL_THREAD -> {
                    builder.offloadExecutor(new VirtualThreadTaskExecutor("grpc-vt-offload-" + address));
                    var epollAvailable = isEpollAvailable();
                    builder.executor(new VirtualThreadTaskExecutor("grpc-vt-exc" + address))
                            .eventLoopGroup(newVirtualThreadEventLoopGroup("grpc-vt-ELG" + address,
                                    availableProcessors(),
                                    epollAvailable))
                            .channelType(epollAvailable ? getEpollSocketChannelClass() : NioSocketChannel.class);
                }
            }
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
