package io.github.m4gshm.payments.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;


@RequiredArgsConstructor
public class KafkaAccountEventServiceImpl implements AccountEventService {

    private final ObjectMapper objectMapper;
    private final ReactiveKafkaProducerTemplate<String, String> template;
    private final String topicName;

    @Override
    @SneakyThrows
    public Mono<SenderResult<Void>> sendAccountTopUp(String clientId) {
        var payload = objectMapper.writeValueAsString(TopUpEvent.builder()
                .clientId(clientId)
                .build());
        return template.send(topicName, payload);
    }

    @Builder
    public record TopUpEvent(String clientId) {

    }
}
