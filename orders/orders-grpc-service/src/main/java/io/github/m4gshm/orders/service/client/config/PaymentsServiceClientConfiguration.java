package io.github.m4gshm.orders.service.client.config;

import io.github.m4gshm.grpc.client.ClientProperties;
import io.grpc.ClientInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import java.util.List;

import static io.github.m4gshm.grpc.client.ClientProperties.newManagedChannelBuilder;
import static payment.v1.PaymentServiceGrpc.newStub;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
public class PaymentsServiceClientConfiguration {
    List<ClientInterceptor> clientInterceptors;

    @Bean
    public PaymentServiceStub paymentsClient() {
        return newStub(newManagedChannelBuilder(paymentsClientProperties(), clientInterceptors).build());
    }

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentsClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public TwoPhaseCommitServiceStub paymentsClientTcp() {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannelBuilder(
                paymentsClientProperties(),
                clientInterceptors
        ).build());
    }

}
