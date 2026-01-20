package io.github.m4gshm;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Constructor;
import java.util.concurrent.ThreadFactory;

@UtilityClass
public class EventLoopGroupUtils {

    private static EventLoopGroup createEpollEventLoopGroup(int parallelism, ThreadFactory threadFactory) {
        try {
            return epollEventLoopGroupConstructor().newInstance(parallelism, threadFactory);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create Epoll EventLoopGroup", e);
        }
    }

    private static Constructor<? extends EventLoopGroup> epollEventLoopGroupConstructor() {
        try {
            return Class
                    .forName("io.netty.channel.epoll.EpollEventLoopGroup")
                    .asSubclass(EventLoopGroup.class)
                    .getConstructor(Integer.TYPE, ThreadFactory.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load EpollEventLoopGroup", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("EpollEventLoopGroup constructor not found", e);
        }
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static Class<? extends ServerChannel> getEpollServerSocketChannelClass() {
        return (Class<? extends ServerChannel>) Class.forName("io.netty.channel.epoll.EpollServerSocketChannel");
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static Class<? extends Channel> getEpollSocketChannelClass() {
        return (Class<? extends Channel>) Class.forName("io.netty.channel.epoll.EpollSocketChannel");
    }

    public static boolean isEpollAvailable() {
        try {
            return (Boolean) Class
                    .forName("io.netty.channel.epoll.Epoll")
                    .getDeclaredMethod("isAvailable")
                    .invoke(null);
        } catch (ClassNotFoundException e) {
            // this is normal if netty-epoll runtime dependency doesn't exist.
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Exception while checking Epoll availability", e);
        }
    }

    public static EventLoopGroup newVirtualThreadEventLoopGroup(String name,
                                                                int numEventLoops,
                                                                boolean epollAvailable) {
        var threadFactory = Thread.ofVirtual().name(name, 0L).factory();
        return epollAvailable
                ? createEpollEventLoopGroup(numEventLoops, threadFactory)
                : new NioEventLoopGroup(numEventLoops, threadFactory);
    }

}
