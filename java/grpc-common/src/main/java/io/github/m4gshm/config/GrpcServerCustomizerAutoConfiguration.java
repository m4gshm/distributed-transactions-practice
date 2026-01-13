package io.github.m4gshm.config;

import grpcstarter.server.GrpcServerCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.time.Duration;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

@AutoConfiguration
@EnableConfigurationProperties(GrpcServerCustomizerAutoConfiguration.GrpcServerProperties.class)
public class GrpcServerCustomizerAutoConfiguration {
    private static Optional<Long> secondsOf(Duration grpcServerProperties) {
        return ofNullable(grpcServerProperties).map(Duration::toSeconds);
    }

    @Bean
    GrpcServerCustomizer grpcServerCustomizerExecutor(GrpcServerProperties grpcServerProperties) {
        return serverBuilder -> {
            switch (grpcServerProperties.executorType) {
                case DIRECT -> serverBuilder.directExecutor();
                case VIRTUAL_THREAD -> serverBuilder.executor(new VirtualThreadTaskExecutor("grpc-server-vt"));
            }
            secondsOf(grpcServerProperties.keepAliveTime()).ifPresent(s -> serverBuilder.keepAliveTime(s, SECONDS));
            secondsOf(grpcServerProperties.keepAliveTimeout()).ifPresent(s -> serverBuilder.keepAliveTimeout(s,
                    SECONDS));
            serverBuilder.permitKeepAliveWithoutCalls(grpcServerProperties.permitKeepAliveWithoutCalls());
            secondsOf(grpcServerProperties.permitKeepAliveTimeInNanos()).ifPresent(s -> serverBuilder
                    .permitKeepAliveTime(s, SECONDS));
        };
    }

    @ConfigurationProperties("grpc.server")
    public record GrpcServerProperties(@DefaultValue("VIRTUAL_THREAD") ExecutorType executorType,
                                       @DefaultValue("1m") Duration keepAliveTime,
                                       @DefaultValue("5s") Duration keepAliveTimeout,
                                       @DefaultValue("true") boolean permitKeepAliveWithoutCalls,
                                       @DefaultValue("1m") Duration permitKeepAliveTimeInNanos
    ) {
        public enum ExecutorType {
                DIRECT, VIRTUAL_THREAD;
        }
    }
}
