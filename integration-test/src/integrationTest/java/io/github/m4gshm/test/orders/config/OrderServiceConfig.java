package io.github.m4gshm.test.orders.config;

import static io.github.m4gshm.test.orders.config.Utils.newManagedChannel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.m4gshm.grpc.client.ClientProperties;
import orders.v1.OrdersServiceGrpc;

@Configuration
@EnableConfigurationProperties
public class OrderServiceConfig {
    @Bean
    public OrdersServiceGrpc.OrdersServiceBlockingStub ordersClient() {
        return OrdersServiceGrpc.newBlockingStub(newManagedChannel(ordersClientProperties()));
    }

    @Bean
    @ConfigurationProperties("service.orders")
    public ClientProperties ordersClientProperties() {
        return new ClientProperties();
    }
}
