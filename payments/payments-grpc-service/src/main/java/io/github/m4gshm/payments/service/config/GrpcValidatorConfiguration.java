package io.github.m4gshm.payments.service.config;

import io.envoyproxy.pgv.ReflectiveValidatorIndex;
import io.envoyproxy.pgv.grpc.ValidatingServerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GrpcValidatorConfiguration {

    @Bean
    public ValidatingServerInterceptor validatingServerInterceptor() {
        return new ValidatingServerInterceptor(new ReflectiveValidatorIndex());
    }
}
