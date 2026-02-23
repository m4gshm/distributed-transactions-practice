package io.github.m4gshm.payments.service.event;

import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KafkaAccountEventServiceImpl implements AccountEventService {

    KafkaTemplate<String, AccountBalanceEvent> sender;
    String topicName;

    @Override
    @SneakyThrows
    public void sendAccountBalanceEvent(String clientId, double balance, OffsetDateTime timestamp) {
        var accountBalanceEvent = AccountBalanceEvent.builder()
                .requestId(UUID.randomUUID().toString())
                .clientId(clientId)
                .balance(balance)
                .timestamp(timestamp)
                .build();
        var send = sender.send(topicName, accountBalanceEvent).get();
        log.debug("balance event sent: recordMetadata {}", send.getRecordMetadata());
    }
}
