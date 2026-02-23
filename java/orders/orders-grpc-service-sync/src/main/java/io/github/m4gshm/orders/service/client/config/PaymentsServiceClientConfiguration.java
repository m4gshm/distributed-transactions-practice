package io.github.m4gshm.orders.service.client.config;

import io.github.m4gshm.grpc.client.ChannelBuilderFactory;
import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payment.v1.PaymentServiceGrpc;
import payment.v1.PaymentServiceGrpc.PaymentServiceBlockingStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceBlockingStub;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class PaymentsServiceClientConfiguration {
    List<ClientInterceptor> clientInterceptors;
    ChannelBuilderFactory<?> channelBuilderFactory;

    private ManagedChannel newManagedChannel() {
        return paymentsClientProperties().newManagedChannelBuilder(channelBuilderFactory, clientInterceptors).build();
    }

    @Bean
    public PaymentServiceBlockingStub paymentsClient() {
        return PaymentServiceGrpc.newBlockingStub(newManagedChannel());
    }

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentsClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceBlockingStub paymentsClientTcp() {
        return TwoPhaseCommitServiceGrpc.newBlockingStub(
                newManagedChannel());
    }
}
