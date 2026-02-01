package io.github.m4gshm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("grpc.server")
public record GrpcServerProperties(@DefaultValue("VIRTUAL_THREAD") ExecutorType executorType,
                                   @DefaultValue("1m") Duration keepAliveTime,
                                   @DefaultValue("5s") Duration keepAliveTimeout,
                                   @DefaultValue("true") boolean permitKeepAliveWithoutCalls,
                                   @DefaultValue("1m") Duration permitKeepAliveTimeInNanos,
                                   @DefaultValue("true") boolean eventLoopGroupUseVirtualThread,
                                   @DefaultValue("false") boolean eventLoopGroupForceNio,
                                   int eventLoopGroupSize
) {
    public enum ExecutorType {
            DIRECT, VIRTUAL_THREAD, UNDEFINED;
    }
}
