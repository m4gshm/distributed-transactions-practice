package io.github.m4gshm.test.orders.config;

import account.v1.AccountServiceGrpc.AccountServiceBlockingStub;
import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static account.v1.AccountServiceGrpc.newBlockingStub;
import static io.github.m4gshm.test.commons.ManagedChannelUtils.newManagedChannel;

@Configuration
@EnableConfigurationProperties
public class AccountServiceConfig {

    @Bean
    public AccountServiceBlockingStub accountService(List<ClientInterceptor> clientInterceptors) {
        return newBlockingStub(newManagedChannel(paymentClientProperties(), clientInterceptors));
    }

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentClientProperties() {
        return new ClientProperties();
    }

}
