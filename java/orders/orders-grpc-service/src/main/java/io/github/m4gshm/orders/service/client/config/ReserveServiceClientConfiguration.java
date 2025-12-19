package io.github.m4gshm.orders.service.client.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.resources.LoopResources;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;
import static io.github.m4gshm.orders.service.client.config.PaymentsServiceClientConfiguration.getEventExecutors;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class ReserveServiceClientConfiguration {
    List<ClientInterceptor> clientInterceptors;

    @Bean
    public ReserveServiceStub reserveClient(ObjectProvider<LoopResources> loopResources) {
        return ReserveServiceGrpc.newStub(newManagedChannelBuilder(
                reserveClientProperties(),
                clientInterceptors,
                getEventExecutors(loopResources)).build());
    }

    @Bean
    @ConfigurationProperties("service.reserve")
    public ClientProperties reserveClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceStub reserveClientTcp(ObjectProvider<LoopResources> loopResources) {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannelBuilder(
                reserveClientProperties(),
                clientInterceptors,
                getEventExecutors(loopResources)
        ).build());
    }

}
