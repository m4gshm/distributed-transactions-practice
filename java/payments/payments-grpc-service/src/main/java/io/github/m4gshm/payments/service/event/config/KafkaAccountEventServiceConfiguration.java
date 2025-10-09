package io.github.m4gshm.payments.service.event.config;

import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import io.github.m4gshm.payments.service.event.AccountEventService;
import io.github.m4gshm.payments.service.event.KafkaAccountEventServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;

import static java.util.Optional.ofNullable;
import static reactor.kafka.sender.SenderOptions.create;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(KafkaAccountEventServiceConfiguration.Properties.class)
public class KafkaAccountEventServiceConfiguration {

    private final KafkaProperties kafkaProperties;
    private final Properties properties;

    @Bean
    public NewTopic accountTopic() {
        var topic = properties.topic;
        var topicBuilder = TopicBuilder.name(topic.name);
        ofNullable(topic.partitions).ifPresent(topicBuilder::partitions);
        ofNullable(topic.replicas).ifPresent(topicBuilder::replicas);
        return topicBuilder.build();
    }

    public ReactiveKafkaProducerTemplate<String, AccountBalanceEvent> accountEventProducerTemplate() {
        return new ReactiveKafkaProducerTemplate<>(create(kafkaProperties.buildProducerProperties()));
    }

    @Bean
    public AccountEventService kafkaEventService() {
        return new KafkaAccountEventServiceImpl(accountEventProducerTemplate(), properties.topic.name);
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
