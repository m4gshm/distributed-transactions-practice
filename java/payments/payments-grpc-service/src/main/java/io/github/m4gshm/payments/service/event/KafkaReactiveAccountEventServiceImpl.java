package io.github.m4gshm.payments.service.event;

import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;

import java.time.OffsetDateTime;
import java.util.UUID;

import static reactor.core.publisher.Mono.just;
import static reactor.kafka.sender.SenderRecord.create;

@Slf4j
@RequiredArgsConstructor
public class KafkaReactiveAccountEventServiceImpl implements ReactiveAccountEventService, DisposableBean {

    private final KafkaSender<String, AccountBalanceEvent> sender;
    private final String topicName;

    @Override
    public void destroy() {
        sender.close();
    }

    @Override
    @SneakyThrows
    public Mono<SenderResult<String>> sendAccountBalanceEvent(String clientId,
                                                              double balance,
                                                              OffsetDateTime timestamp) {
        var accountBalanceEvent = AccountBalanceEvent.builder()
                .requestId(UUID.randomUUID().toString())
                .clientId(clientId)
                .balance(balance)
                .timestamp(timestamp)
                .build();

        var senderRecord = create(topicName,
                null,
                timestamp.toInstant().toEpochMilli(),
                (String) null,
                accountBalanceEvent,
                clientId);

        return sender.send(just(senderRecord))
                .doOnError(e -> log.error("balance event sending failed", e))
                .doOnNext(r -> {
                    log.debug("balance event sent: correlationMetadata {}, recordMetadata {}",
                            r.correlationMetadata(),
                            r.recordMetadata());
                })
                .next();

    }
}
