package io.github.m4gshm.payments.service.integration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.m4gshm.payments.service.integration.AccountEventService;
import io.github.m4gshm.payments.service.integration.KafkaAccountEventServiceImpl;
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
@EnableConfigurationProperties(KafkaConfiguration.Properties.class)
public class KafkaConfiguration {

    private final KafkaProperties kafkaProperties;
    private final Properties properties;

    @Bean
    public NewTopic topic() {
        var topic = properties.topic;
        var topicBuilder = TopicBuilder.name(topic.name);
        ofNullable(topic.partitions).ifPresent(topicBuilder::partitions);
        ofNullable(topic.replicas).ifPresent(topicBuilder::replicas);
        return topicBuilder.build();
    }

    @Bean
    ReactiveKafkaProducerTemplate<String, String> template() {
        return new ReactiveKafkaProducerTemplate<>(create(kafkaProperties.buildProducerProperties()));
    }

    @Bean
    public AccountEventService kafkaEventService(ObjectMapper objectMapper,
                                                 ReactiveKafkaProducerTemplate<String, String> template) {
        return new KafkaAccountEventServiceImpl(objectMapper, template, properties.topic.name);
    }

    @ConfigurationProperties("service.kafka")
    public record Properties(@DefaultValue Topic topic) {
        public record Topic(
                @DefaultValue("payment") String name,
                @DefaultValue("1") Integer partitions,
                @DefaultValue("1") Integer replicas
        ) {
        }
    }
}
