package io.github.m4gshm.reactive.config;

import grpcstarter.extensions.transcoding.DefaultReactiveTranscoder;
import grpcstarter.extensions.transcoding.GrpcTranscodingAutoConfiguration;
import grpcstarter.extensions.transcoding.GrpcTranscodingProperties;
import grpcstarter.extensions.transcoding.HeaderConverter;
import grpcstarter.extensions.transcoding.ReactiveTranscodingExceptionResolver;
import grpcstarter.extensions.transcoding.TranscodingCustomizer;
import grpcstarter.server.GrpcServerProperties;
import grpcstarter.server.GrpcServerStartedEvent;
import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Optional;

@Slf4j
@AutoConfiguration(before = GrpcTranscodingAutoConfiguration.class)
public class DefaultReactiveTranscoderAutoConfiguration {

    @Bean
    public DefaultReactiveTranscoder grpcStarterDefaultReactiveTranscoder(
                                                                          List<BindableService> services,
                                                                          HeaderConverter headerConverter,
                                                                          GrpcTranscodingProperties grpcTranscodingProperties,
                                                                          GrpcServerProperties grpcServerProperties,
                                                                          ReactiveTranscodingExceptionResolver transcodingExceptionResolver,
                                                                          List<TranscodingCustomizer> transcodingCustomizers) {
        return new DefaultReactiveTranscoder(
                services,
                headerConverter,
                grpcTranscodingProperties,
                grpcServerProperties,
                transcodingExceptionResolver,
                transcodingCustomizers) {
            public static Channel getTranscodingChannel(
                                                        int port,
                                                        GrpcTranscodingProperties grpcTranscodingProperties,
                                                        GrpcServerProperties grpcServerProperties) {
                var inProcess = grpcServerProperties.getInProcess();
                if (inProcess != null && StringUtils.hasText(inProcess.name())) {
                    var builder = InProcessChannelBuilder.forName(inProcess.name());
                    populateChannel(builder, grpcServerProperties);
                    return builder.build();
                }

                String endpoint = StringUtils.hasText(grpcTranscodingProperties.getEndpoint())
                        ? grpcTranscodingProperties.getEndpoint()
                        : "localhost:" + port;
                var builder = ManagedChannelBuilder.forTarget(endpoint);
                // copied and pasted only for directExecutor
                builder.directExecutor();
                try {
                    builder.offloadExecutor(new VirtualThreadTaskExecutor(
                            "grpc-offload-transcoding" + endpoint
                    ));
                } catch (UnsupportedOperationException _) {
                    log.info("transcoding virtual offloadExecutor not supported for {}", endpoint);
                }

                populateChannel(builder, grpcServerProperties);
                if (!StringUtils.hasText(grpcServerProperties.getSslBundle())) {
                    builder.usePlaintext();
                }
                return builder.build();
            }

            private static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>> populateChannel(
                                                                                                     ManagedChannelBuilder<
                                                                                                             ? extends ManagedChannelBuilder<
                                                                                                                     ?>> channelBuilder,
                                                                                                     GrpcServerProperties grpcServerProperties) {

                Optional.ofNullable(grpcServerProperties.getMaxInboundMessageSize())
                        .map(DataSize::toBytes)
                        .map(Long::intValue)
                        .ifPresent(channelBuilder::maxInboundMessageSize);
                Optional.ofNullable(grpcServerProperties.getMaxInboundMetadataSize())
                        .map(DataSize::toBytes)
                        .map(Long::intValue)
                        .ifPresent(channelBuilder::maxInboundMetadataSize);

                return channelBuilder;
            }

            @Override
            @SneakyThrows
            public void onApplicationEvent(GrpcServerStartedEvent event) {
                var channelField = this.getClass().getSuperclass().getDeclaredField("channel");
                channelField.setAccessible(true);
                var channel = getTranscodingChannel(event.getSource().getPort(),
                        grpcTranscodingProperties,
                        grpcServerProperties);
                channelField.set(this, channel);
            }
        };
    }

}
