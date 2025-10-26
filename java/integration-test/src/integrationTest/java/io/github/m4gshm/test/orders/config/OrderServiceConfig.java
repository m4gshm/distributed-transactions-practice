package io.github.m4gshm.test.orders.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import orders.v1.OrderServiceGrpc;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static io.github.m4gshm.test.orders.config.Utils.newManagedChannel;

@Configuration
@EnableConfigurationProperties
public class OrderServiceConfig {
    @Bean
    public OrderServiceGrpc.OrderServiceBlockingStub ordersClient(List<ClientInterceptor> clientInterceptors) {
        return OrderServiceGrpc.newBlockingStub(newManagedChannel(ordersClientProperties(), clientInterceptors));
    }

    @Bean
    @ConfigurationProperties("service.orders")
    public ClientProperties ordersClientProperties() {
        return new ClientProperties();
    }
}
