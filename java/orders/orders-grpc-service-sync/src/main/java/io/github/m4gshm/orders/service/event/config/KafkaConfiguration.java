package io.github.m4gshm.orders.service.event.config;

import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@Configuration
@EnableKafka
@RequiredArgsConstructor
public class KafkaConfiguration {
    private final KafkaProperties kafkaProperties;

    @Bean
    public ConsumerFactory<String, AccountBalanceEvent> accountBalanceEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(),
                new StringDeserializer(),
                new JacksonJsonDeserializer<>(AccountBalanceEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AccountBalanceEvent>
           accountBalanceEventListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, AccountBalanceEvent>();
        factory.setConsumerFactory(accountBalanceEventConsumerFactory());
        return factory;
    }
}
