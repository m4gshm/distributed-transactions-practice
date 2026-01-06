package io.github.m4gshm.orders;

//import org.springframework.boot.grpc.server.autoconfigure.GrpcServerProperties;

//import org.springframework.grpc.server.lifecycle.GrpcServerStartedEvent;

//import static grpcstarter.extensions.transcoding.Util.getTranscodingChannel;

//import static grpcstarter.extensions.transcoding.Util.getTranscodingChannel;

//@Configuration
public class GrpcTranscodingConfiguration {
//    public static Channel getTranscodingChannel(
//                                                int port,
//                                                GrpcTranscodingProperties grpcTranscodingProperties,
//                                                GrpcServerProperties grpcServerProperties) {
//        var inProcess = grpcServerProperties.getInprocess();
//        if (inProcess != null && StringUtils.hasText(inProcess.getName())) {
//            var builder = InProcessChannelBuilder.forName(inProcess.getName());
//            populateChannel(builder, grpcServerProperties);
//            return builder.build();
//        }
//
//        String endpoint = StringUtils.hasText(grpcTranscodingProperties.getEndpoint())
//                ? grpcTranscodingProperties.getEndpoint()
//                : "localhost:" + port;
//        var builder = ManagedChannelBuilder.forTarget(endpoint);
//        populateChannel(builder, grpcServerProperties);
//        if (!StringUtils.hasText(grpcServerProperties.getSsl().getBundle())) {
//            builder.usePlaintext();
//        }
//        return builder.build();
//    }

//    private static ManagedChannelBuilder<? extends ManagedChannelBuilder<?>> populateChannel(
//                                                                                             ManagedChannelBuilder<
//                                                                                                     ? extends ManagedChannelBuilder<
//                                                                                                             ?>> channelBuilder,
//                                                                                             GrpcServerProperties grpcServerProperties) {
//
//        Optional.ofNullable(grpcServerProperties.getMaxInboundMessageSize())
//                .map(DataSize::toBytes)
//                .map(Long::intValue)
//                .ifPresent(channelBuilder::maxInboundMessageSize);
//        Optional.ofNullable(grpcServerProperties.getMaxInboundMetadataSize())
//                .map(DataSize::toBytes)
//                .map(Long::intValue)
//                .ifPresent(channelBuilder::maxInboundMetadataSize);
//
//        return channelBuilder;
//    }
//
    ////    @Bean
////    @ConditionalOnMissingBean(ServletTranscoder.class)
//    public ServletTranscoder grpcStarterDefaultServletTranscoder(
//                                                                 List<BindableService> services,
//                                                                 HeaderConverter headerConverter,
//                                                                 GrpcTranscodingProperties grpcTranscodingProperties,
//                                                                 GrpcServerProperties grpcServerProperties,
//                                                                 TranscodingExceptionResolver transcodingExceptionResolver,
//                                                                 List<TranscodingCustomizer> transcodingCustomizers,
//                                                                 GrpcTranscodingPropertiesMapper grpcTranscodingPropertiesMapper
//    ) {
//        var grpcServerProperties1 = grpcTranscodingPropertiesMapper.map(grpcServerProperties);
//        return new DefaultServletTranscoder(
//                services,
//                headerConverter,
//                grpcTranscodingProperties,
//                grpcServerProperties1,
//                transcodingExceptionResolver,
//                transcodingCustomizers) {
//
//            @SneakyThrows
//            @EventListener(GrpcServerStartedEvent.class)
//            public void onApplicationEvent(GrpcServerStartedEvent event) {
//                var field = getClass().getSuperclass().getDeclaredField("channel");
//                field.setAccessible(true);
//                var channel = getTranscodingChannel(event.getSource().getPort(),
//                        grpcTranscodingProperties,
//                        grpcServerProperties);
//                field.set(this, channel);
//            }
//        };
//    }
}
