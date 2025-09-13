package io.github.m4gshm.reactive.config;

import io.github.m4gshm.reactive.RestExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.HttpMessageReader;

import java.util.List;

@AutoConfiguration
public class RestExceptionHandlerAutoConfiguration {
    @Bean
    public RestExceptionHandler restExceptionHandler(ErrorAttributes errorAttributes,
                                                     List<HttpMessageReader<?>> messageReaders) {
        return new RestExceptionHandler(errorAttributes, messageReaders);
    }
}
