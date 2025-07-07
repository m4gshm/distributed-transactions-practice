package orders.service.client.config;

import io.grpc.ManagedChannelBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reserve.v1.ReserveServiceGrpc;

import static io.grpc.ManagedChannelBuilder.forTarget;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ReserveServiceClientConfiguration.Properties.class)
public class ReserveServiceClientConfiguration {

    @Bean
    public ReserveServiceGrpc.ReserveServiceStub reserveClient(Properties properties) {
        var builder = forTarget(properties.address());
        if (properties.noSecure) {
            builder.usePlaintext();
        }
        return ReserveServiceGrpc.newStub(builder.build());
    }

    @ConfigurationProperties("service.reserve")
    public record Properties(@DefaultValue("localhost:8888") String address,
                             @DefaultValue("true") boolean noSecure) {
    }
}
