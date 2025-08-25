package io.github.m4gshm.orders.service.client.config;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;
import static payment.v1.PaymentServiceGrpc.newStub;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.m4gshm.grpc.client.ClientProperties;
import lombok.RequiredArgsConstructor;
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

@Configuration
@RequiredArgsConstructor
public class PaymentsServiceClientConfiguration {

    @Bean
    public PaymentServiceStub paymentsClient(ClientProperties paymentsClientProperties) {
        return newStub(newManagedChannelBuilder(paymentsClientProperties).build());
    }

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentsClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceStub paymentsClientTcp(ClientProperties paymentsClientProperties) {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannelBuilder(paymentsClientProperties).build());
    }

}
