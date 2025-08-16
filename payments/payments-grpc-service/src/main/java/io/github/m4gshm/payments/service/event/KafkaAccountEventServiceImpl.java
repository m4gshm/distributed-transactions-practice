package io.github.m4gshm.payments.service.event;

import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;

import java.util.UUID;

@RequiredArgsConstructor
public class KafkaAccountEventServiceImpl implements AccountEventService {

    private final ReactiveKafkaProducerTemplate<String, AccountBalanceEvent> template;
    private final String topicName;

    @Override
    @SneakyThrows
    public Mono<SenderResult<Void>> sendAccountBalanceEvent(String clientId, double balance) {
        return template.send(topicName,
                             AccountBalanceEvent.builder()
                                                .requestId(UUID.randomUUID().toString())
                                                .clientId(clientId)
                                                .balance(balance)
                                                .build());
    }
}
