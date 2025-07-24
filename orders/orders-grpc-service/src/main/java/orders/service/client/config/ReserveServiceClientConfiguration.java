package orders.service.client.config;

import lombok.RequiredArgsConstructor;
import orders.service.client.ClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;
import tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub;

import static orders.service.client.ClientProperties.newManagedChannelBuilder;

@Configuration
@RequiredArgsConstructor
public class ReserveServiceClientConfiguration {

    @Bean
    @ConfigurationProperties("service.reserve")
    public ClientProperties reserveClientProperties() {
        return new ClientProperties();
    }

    @Bean
    public ReserveServiceStub reserveClient(ClientProperties reserveClientProperties) {
        return ReserveServiceGrpc.newStub(newManagedChannelBuilder(reserveClientProperties).build());
    }

    @Bean
    public TwoPhaseCommitServiceStub reserveClientTcp(ClientProperties reserveClientProperties) {
        return TwoPhaseCommitServiceGrpc.newStub(newManagedChannelBuilder(reserveClientProperties).build());
    }

}
