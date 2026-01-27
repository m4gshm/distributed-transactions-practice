package io.github.m4gshm.orders.service;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpGrpcSpanExporterBuilderCustomizer;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnClass(OtlpGrpcSpanExporterBuilder.class)
public class VirtualThreadExecutorOtlpGrpcSpanExporterBuilderCustomizer implements
        OtlpGrpcSpanExporterBuilderCustomizer {
    @Override
    public void customize(OtlpGrpcSpanExporterBuilder builder) {
        builder.setExecutorService(new VirtualThreadExecutor("otlp-grpc-export"));
    }
}
