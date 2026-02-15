package io.github.m4gshm.tracing;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpGrpcSpanExporterBuilderCustomizer;

public class VirtualThreadExecutorOtlpGrpcSpanExporterBuilderCustomizer implements
        OtlpGrpcSpanExporterBuilderCustomizer {
    @Override
    public void customize(OtlpGrpcSpanExporterBuilder builder) {
        builder.setExecutorService(new VirtualThreadExecutor("otlp-export"));
    }
}
