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
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.List;

import static payment.v1.PaymentServiceGrpc.newStub;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class PaymentsServiceClientConfiguration {
    List<ClientInterceptor> clientInterceptors;
    ChannelBuilderFactory<?> channelBuilderFactory;

    private ManagedChannel newManagedChannel(ClientProperties clientProperties) {
        return clientProperties.newManagedChannelBuilder(channelBuilderFactory, clientInterceptors).build();
    }

    @Bean
    public PaymentServiceStub paymentsClient() {
        return newStub(newManagedChannel(paymentsClientProperties()));
    }

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentsClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceStub paymentsClientTcp() {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannel(paymentsClientProperties()));
    }

}
