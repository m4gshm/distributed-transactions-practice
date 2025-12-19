package io.github.m4gshm.orders.service.client.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import io.netty.channel.EventLoopGroup;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import reactor.netty.resources.LoopResources;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;
import static java.util.Optional.ofNullable;
import static payment.v1.PaymentServiceGrpc.newStub;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class PaymentsServiceClientConfiguration {
    List<ClientInterceptor> clientInterceptors;

    public static @Nullable EventLoopGroup getEventExecutors(ObjectProvider<LoopResources> loopResources) {
        return ofNullable(loopResources.getIfAvailable())
                .map(lr -> lr.onClient(true))
                .orElse(null);
    }

    @Bean
    public PaymentServiceStub paymentsClient(ObjectProvider<LoopResources> loopResources) {
        return newStub(newManagedChannelBuilder(paymentsClientProperties(),
                clientInterceptors,
                getEventExecutors(loopResources)
        ).build());
    }

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentsClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceStub paymentsClientTcp(ObjectProvider<LoopResources> loopResources) {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannelBuilder(
                paymentsClientProperties(),
                clientInterceptors,
                getEventExecutors(loopResources)
        ).build());
    }

}
