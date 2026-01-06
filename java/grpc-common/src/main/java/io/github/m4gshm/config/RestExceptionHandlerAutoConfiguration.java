package io.github.m4gshm.config;

import io.github.m4gshm.RestControllerExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class RestExceptionHandlerAutoConfiguration {
    @Bean
    public RestControllerExceptionHandler restExceptionHandler(
                                                               org.springframework.boot.webmvc.error.ErrorAttributes errorAttributes1) {
        return new RestControllerExceptionHandler(errorAttributes1);
    }
}
