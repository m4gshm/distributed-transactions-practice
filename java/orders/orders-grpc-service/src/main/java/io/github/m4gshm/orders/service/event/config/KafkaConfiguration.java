package io.github.m4gshm.orders.service.event.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

    private final KafkaProperties kafkaProperties;

    @Bean
    public ReceiverOptions<String, String> balanceReceiverOptions() {
        return ReceiverOptions.<String, String>create(kafkaProperties.buildConsumerProperties())
                .subscription(List.of("balance"));
    }

}
