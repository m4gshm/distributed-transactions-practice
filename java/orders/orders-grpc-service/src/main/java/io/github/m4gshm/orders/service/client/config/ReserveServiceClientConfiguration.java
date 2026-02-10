package io.github.m4gshm.orders.service.client.config;

import io.github.m4gshm.grpc.client.ChannelBuilderFactory;
import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class ReserveServiceClientConfiguration {
    List<ClientInterceptor> clientInterceptors;
    ChannelBuilderFactory<?> channelBuilderFactory;

    private ManagedChannel newManagedChannel() {
        return newManagedChannelBuilder(reserveClientProperties(), clientInterceptors, channelBuilderFactory).build();
    }

    @Bean
    public ReserveServiceStub reserveClient() {
        return ReserveServiceGrpc.newStub(newManagedChannel());
    }

    @Bean
    @ConfigurationProperties("service.reserve")
    public ClientProperties reserveClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceStub reserveClientTcp() {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannel());
    }
}
