package io.github.m4gshm.orders.service.client.config;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.m4gshm.grpc.client.ClientProperties;
import lombok.RequiredArgsConstructor;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

@Configuration
@RequiredArgsConstructor
public class ReserveServiceClientConfiguration {

    @Bean
    public ReserveServiceStub reserveClient(ClientProperties reserveClientProperties) {
        return ReserveServiceGrpc.newStub(newManagedChannelBuilder(reserveClientProperties).build());
    }

    @Bean
    @ConfigurationProperties("service.reserve")
    public ClientProperties reserveClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceStub reserveClientTcp(ClientProperties reserveClientProperties) {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannelBuilder(reserveClientProperties).build());
    }

}
