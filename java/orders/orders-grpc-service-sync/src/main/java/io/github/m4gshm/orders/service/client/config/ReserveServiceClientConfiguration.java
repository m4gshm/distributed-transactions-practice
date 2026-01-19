package io.github.m4gshm.orders.service.client.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceGrpc.ReserveServiceBlockingStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceBlockingStub;

import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class ReserveServiceClientConfiguration {
    List<ClientInterceptor> clientInterceptors;

    @Bean
    public ReserveServiceBlockingStub reserveClient() {
        var nettyChannelBuilder = newManagedChannelBuilder(
                reserveClientProperties(),
                clientInterceptors
        );
        return ReserveServiceGrpc.newBlockingStub(nettyChannelBuilder.build());
    }

    @Bean
    @ConfigurationProperties("service.reserve")
    public ClientProperties reserveClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceBlockingStub reserveClientTcp() {
        return TwoPhaseCommitServiceGrpc.newBlockingStub(newManagedChannelBuilder(
                reserveClientProperties(),
                clientInterceptors
        ).build());
    }

}
