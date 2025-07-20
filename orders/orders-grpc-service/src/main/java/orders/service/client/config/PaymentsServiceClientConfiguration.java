package orders.service.client.config;

import io.grpc.ManagedChannelBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payments.v1.PaymentsServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import static orders.service.client.config.ClientProperties.newManagedChannelBuilder;

@Configuration
@RequiredArgsConstructor
public class PaymentsServiceClientConfiguration {

    @Bean
    @ConfigurationProperties("service.payments")
    public ClientProperties paymentsClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public PaymentsServiceGrpc.PaymentsServiceStub paymentsClient(ClientProperties paymentsClientProperties) {
        return PaymentsServiceGrpc.newStub(newManagedChannelBuilder(paymentsClientProperties).build());
    }

    @Bean
    public TwoPhaseCommitServiceStub paymentsClientTcp(ClientProperties paymentsClientProperties) {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannelBuilder(paymentsClientProperties).build());
    }

}
