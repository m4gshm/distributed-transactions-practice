package io.github.m4gshm.orders.service.event.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

    private final KafkaProperties kafkaProperties;

//    @Bean
//    public ReceiverOptions<String, AccountBalanceEvent> balanceReceiverOptions() {
//        return ReceiverOptions.<String, AccountBalanceEvent>create(kafkaProperties.buildConsumerProperties())
//                .subscription(List.of("balance"));
//    }

}
