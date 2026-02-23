package io.github.m4gshm.test.orders.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import warehouse.v1.WarehouseItemServiceGrpc;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceBlockingStub;

import java.util.List;

import static io.github.m4gshm.test.commons.ManagedChannelUtils.newManagedChannel;

@Configuration
@EnableConfigurationProperties
public class WarehouseItemServiceConfig {

    @Bean
    @ConfigurationProperties("service.warehouse")
    public ClientProperties warehouseClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public WarehouseItemServiceBlockingStub warehouseItemService(List<ClientInterceptor> clientInterceptors) {
        return WarehouseItemServiceGrpc.newBlockingStub(newManagedChannel(
                warehouseClientProperties(),
                clientInterceptors
        ));
    }
}
