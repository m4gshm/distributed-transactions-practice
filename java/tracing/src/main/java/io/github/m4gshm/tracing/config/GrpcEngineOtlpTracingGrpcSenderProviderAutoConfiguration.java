package io.github.m4gshm.tracing.config;

import io.github.m4gshm.grpc.client.ChannelBuilderFactory;
import io.github.m4gshm.grpc.client.ExecutorType;
import io.opentelemetry.exporter.internal.grpc.GrpcSenderProvider;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.exporter.sender.grpc.managedchannel.internal.UpstreamGrpcSenderProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpGrpcSpanExporterBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.context.annotation.Bean;

import static io.github.m4gshm.grpc.client.ExecutorType.VIRTUAL_THREAD;
import static io.github.m4gshm.grpc.client.ManagedChanelBuilderUtils.newManagedChannelBuilder;
import static io.opentelemetry.exporter.internal.ExporterBuilderUtil.validateEndpoint;
import static java.util.concurrent.TimeUnit.MINUTES;

@ConditionalOnClass({
        GrpcSenderProvider.class,
        OtlpGrpcSpanExporterBuilderCustomizer.class,
        OtlpGrpcSpanExporterBuilder.class,
})
@AutoConfiguration(before = OtlpTracingAutoConfiguration.class)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "management.opentelemetry.tracing.export.otlp.transport", havingValue = "grpc")
public class GrpcEngineOtlpTracingGrpcSenderProviderAutoConfiguration {

    @Bean
    @ConditionalOnProperty(value = "management.opentelemetry.tracing.export.otlp.grpc.engine",
            havingValue = "upstream",
            matchIfMissing = true)
    @ConditionalOnClass({OtlpTracingConnectionDetails.class, UpstreamGrpcSenderProvider.class})
    @ConditionalOnBean(ChannelBuilderFactory.class)
    public OtlpGrpcSpanExporterBuilderCustomizer
    otlpGrpcSpanExporterBuilderCustomizerUpstreamGrpcSenderProvider(
            OtlpTracingConnectionDetails connectionDetails, ChannelBuilderFactory<?> channelBuilderFactory,
            @Value("${management.opentelemetry.tracing.export.otlp.grpc.executor-type:VIRTUAL_THREAD}") ExecutorType executorType
    ) {
        return builder -> {
            System.getProperties()
                    .put(
                            "io.opentelemetry.exporter.internal.grpc.GrpcSenderProvider",
                            UpstreamGrpcSenderProvider.class.getName()
                    );

            var uri = validateEndpoint(connectionDetails.getUrl(Transport.GRPC));
            var address = uri.getHost() + ":" + uri.getPort();
            var channelBuilder = newManagedChannelBuilder(channelBuilderFactory,
                    address,
                    executorType != null ? executorType : VIRTUAL_THREAD);
            var isPlainHttp = "http".equals(uri.getScheme());
            if (isPlainHttp) {
                channelBuilder.usePlaintext();
            }
            builder.setChannel(channelBuilder.keepAliveTime(30, MINUTES).build());
        };
    }

}
