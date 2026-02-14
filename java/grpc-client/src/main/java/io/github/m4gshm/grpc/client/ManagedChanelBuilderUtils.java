package io.github.m4gshm.grpc.client;

import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

import static io.github.m4gshm.EventLoopGroupUtils.getEpollSocketChannelClass;
import static io.github.m4gshm.EventLoopGroupUtils.isEpollAvailable;
import static io.github.m4gshm.EventLoopGroupUtils.newVirtualThreadEventLoopGroup;
import static io.netty.util.NettyRuntime.availableProcessors;

@UtilityClass
public class ManagedChanelBuilderUtils {
    @SneakyThrows
    public static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>> newManagedChannelBuilder(
                                                                                                     ChannelBuilderFactory<
                                                                                                             ?> channelBuilderFactory,
                                                                                                     String address,
                                                                                                     ExecutorType executorType) {
        var parts = address.split(":");
        var host = parts[0];
        int port = Integer.parseInt(parts[1]);
        var addr = Inet4Address.getByName(host);
        var builder = channelBuilderFactory.apply(new InetSocketAddress(addr, port));

        if (executorType != null) {
            switch (executorType) {
                case DIRECT -> builder.directExecutor();
                case VIRTUAL_THREAD -> useVirtualThreads(builder, address);
            }
        }
        return builder;
    }

    public static void useVirtualThreads(ManagedChannelBuilder<?> builder, String suffix) {
        var virtualThreadTaskExecutor = new VirtualThreadTaskExecutor("grpc-vt-" + suffix);
        builder.offloadExecutor(virtualThreadTaskExecutor);
        builder.executor(virtualThreadTaskExecutor);

        if (builder instanceof NettyChannelBuilder nettyChannelBuilder) {
            var epollAvailable = isEpollAvailable();
            nettyChannelBuilder.eventLoopGroup(newVirtualThreadEventLoopGroup("grpc-vt-ELG" + suffix,
                    availableProcessors(),
                    epollAvailable))
                    .channelType(epollAvailable ? getEpollSocketChannelClass() : NioSocketChannel.class);
        } else if (builder instanceof OkHttpChannelBuilder okHttpChannelBuilder) {
            okHttpChannelBuilder.transportExecutor(virtualThreadTaskExecutor);
        }
    }
}
