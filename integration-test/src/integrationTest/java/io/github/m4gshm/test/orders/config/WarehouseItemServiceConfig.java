package io.github.m4gshm.test.orders.config;

import static io.github.m4gshm.test.orders.config.Utils.newManagedChannel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.m4gshm.grpc.client.ClientProperties;
import warehouse.v1.WarehouseItemServiceGrpc;

@Configuration
@EnableConfigurationProperties
public class WarehouseItemServiceConfig {

    @Bean
    @ConfigurationProperties("service.warehouse")
    public ClientProperties warehouseClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public WarehouseItemServiceGrpc.WarehouseItemServiceBlockingStub warehouseItemService() {
        return WarehouseItemServiceGrpc.newBlockingStub(newManagedChannel(warehouseClientProperties()));
    }
}
