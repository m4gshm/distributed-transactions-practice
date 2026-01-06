package io.github.m4gshm.tracing.config;

import io.opentelemetry.contrib.sampler.RuleBasedRoutingSampler;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;

@Configuration
public class OtelCustomizerAutoConfiguration {

    // otel sdk impl
    @Bean
    @ConditionalOnClass({ AutoConfigurationCustomizerProvider.class, RuleBasedRoutingSampler.class })
    public AutoConfigurationCustomizerProvider actuatorDropOtelCustomizer() {
        return customizer -> customizer.addSamplerCustomizer((fallback, config) -> {
            return RuleBasedRoutingSampler.builder(SERVER, fallback)
                    .drop(URL_PATH, "^/actuator")
                    .build();
        });
    }

}
