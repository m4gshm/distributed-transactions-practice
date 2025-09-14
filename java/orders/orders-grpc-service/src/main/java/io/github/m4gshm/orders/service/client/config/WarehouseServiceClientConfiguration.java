package io.github.m4gshm.orders.service.client.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceStub;

import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;
import static warehouse.v1.WarehouseItemServiceGrpc.newStub;

@Configuration
@RequiredArgsConstructor
public class WarehouseServiceClientConfiguration {

    @Bean
    public WarehouseItemServiceStub warehouseClient(
                                                    ClientProperties warehouseClientProperties,
                                                    List<ClientInterceptor> clientInterceptors
    ) {
        return newStub(newManagedChannelBuilder(warehouseClientProperties, clientInterceptors).build());
    }

    @Bean
    @ConfigurationProperties("service.warehouse")
    public ClientProperties warehouseClientProperties() {
        return new ClientProperties();
    }

}
