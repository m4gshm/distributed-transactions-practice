package io.github.m4gshm.tracing.config;

import io.github.m4gshm.tracing.VirtualThreadExecutorOtlpGrpcSpanExporterBuilderCustomizer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ OtlpGrpcSpanExporterBuilder.class, VirtualThreadExecutor.class })
public class VirtualThreadExecutorOtlpGrpcSpanExporterBuilderCustomizerAutoConfiguration {
    @Bean
    public VirtualThreadExecutorOtlpGrpcSpanExporterBuilderCustomizer
           virtualThreadExecutorOtlpGrpcSpanExporterBuilderCustomizer() {
        return new VirtualThreadExecutorOtlpGrpcSpanExporterBuilderCustomizer();
    }
}
