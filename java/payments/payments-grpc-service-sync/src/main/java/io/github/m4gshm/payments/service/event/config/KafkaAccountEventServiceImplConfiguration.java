package io.github.m4gshm.payments.service.event.config;

import io.github.m4gshm.payments.service.event.AccountEventService;
import io.github.m4gshm.payments.service.event.KafkaAccountEventServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(KafkaAccountEventServiceImplConfiguration.Properties.class)
public class KafkaAccountEventServiceImplConfiguration {

    private final KafkaProperties kafkaProperties;
    private final Properties properties;

    @Bean
    public AccountEventService accountEventService() {
        return new KafkaAccountEventServiceImpl(/* sender, */properties.topic.name);
    }

    @ConfigurationProperties("service.kafka.account")
    public record Properties(@DefaultValue Topic topic) {
        public record Topic(
                            @DefaultValue("balance") String name,
                            @DefaultValue("1") Integer partitions,
                            @DefaultValue("1") Integer replicas) {
        }
    }
}
