package io.github.m4gshm.config;

import io.github.m4gshm.RestControllerExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class RestExceptionHandlerAutoConfiguration {
    @Bean
    @ConditionalOnBean(ErrorAttributes.class)
    public RestControllerExceptionHandler restExceptionHandler(
                                                               ErrorAttributes errorAttributes1) {
        return new RestControllerExceptionHandler(errorAttributes1);
    }
}
