package io.github.m4gshm.reactive.config;

import io.github.m4gshm.reactive.ReactiveRestExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.HttpMessageReader;

import java.util.List;

@AutoConfiguration
public class ReactiveRestExceptionHandlerAutoConfiguration {
    @Bean
    @ConditionalOnBean(ErrorAttributes.class)
    public ReactiveRestExceptionHandler reactiveRestExceptionHandler(ErrorAttributes errorAttributes,
                                                                     List<HttpMessageReader<?>> messageReaders) {
        return new ReactiveRestExceptionHandler(errorAttributes, messageReaders);
    }
}
