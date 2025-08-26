package io.github.m4gshm.orders.service.client.config;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.m4gshm.grpc.client.ClientProperties;
import lombok.RequiredArgsConstructor;
import warehouse.v1.WarehouseItemServiceGrpc;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceStub;

@Configuration
@RequiredArgsConstructor
public class WarehouseServiceClientConfiguration {

    @Bean
    public WarehouseItemServiceStub warehouseClient(ClientProperties warehouseClientProperties) {
        return WarehouseItemServiceGrpc.newStub(newManagedChannelBuilder(warehouseClientProperties).build());
    }

    @Bean
    @ConfigurationProperties("service.warehouse")
    public ClientProperties warehouseClientProperties() {
        return new ClientProperties();
    }

}
