package io.github.m4gshm.orders.service.event.config;

import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
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
    public ReceiverOptions<String, AccountBalanceEvent> balanceReceiverOptions() {
        return ReceiverOptions.<String, AccountBalanceEvent>create(kafkaProperties.buildConsumerProperties())
                .subscription(List.of("balance"));
    }

}
