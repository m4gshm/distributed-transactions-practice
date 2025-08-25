package io.github.m4gshm.test.orders.config;

import static io.github.m4gshm.test.orders.config.Utils.newManagedChannel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.m4gshm.grpc.client.ClientProperties;
import payment.v1.AccountServiceGrpc;

@Configuration
@EnableConfigurationProperties
public class AccountServiceConfig {

    @Bean
    public AccountServiceGrpc.AccountServiceBlockingStub accountService() {
        return AccountServiceGrpc.newBlockingStub(newManagedChannel(paymentClientProperties()));
    }

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentClientProperties() {
        return new ClientProperties();
    }

}
