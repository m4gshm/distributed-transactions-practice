package io.github.m4gshm.payments.config;

import static org.springframework.util.Assert.isTrue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
//@RequiredArgsConstructor
public class WebFluxJackson2Configuration implements WebFluxConfigurer {
    public static final String JACKSON_DATATYPE_JSR_310 = "jackson-datatype-jsr310";

    private final ObjectMapper objectMapper;

    public WebFluxJackson2Configuration(Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder) {
        var objectMapper = jackson2ObjectMapperBuilder.modulesToInstall(modules -> {
            // do nothing, need only for set then findWellKnownModules to true
            // after applying springDocBridgeProtobufJackson2ObjectMapperBuilderCustomizer
        }).build();
        isTrue(
                objectMapper.getRegisteredModuleIds().contains(JACKSON_DATATYPE_JSR_310),
                JACKSON_DATATYPE_JSR_310 + " not registered");
        this.objectMapper = objectMapper;
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        var defaultCodecs = configurer.defaultCodecs();
        defaultCodecs.jackson2JsonEncoder(jackson2JsonEncoder());
        defaultCodecs.jackson2JsonDecoder(jackson2JsonDecoder());
    }

    @Bean
    Jackson2JsonDecoder jackson2JsonDecoder() {
        return new Jackson2JsonDecoder(objectMapper);
    }

    @Bean
    Jackson2JsonEncoder jackson2JsonEncoder() {
        return new Jackson2JsonEncoder(objectMapper);
    }

}
