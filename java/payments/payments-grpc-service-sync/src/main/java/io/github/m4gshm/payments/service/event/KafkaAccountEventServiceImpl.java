package io.github.m4gshm.payments.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.time.OffsetDateTime;

@Slf4j
@RequiredArgsConstructor
public class KafkaAccountEventServiceImpl implements AccountEventService, DisposableBean {

    // private final KafkaSender<String, AccountBalanceEvent> sender;
    private final String topicName;

    @Override
    public void destroy() {
//        sender.close();
    }

    @Override
//    @SneakyThrows
    public void sendAccountBalanceEvent(String clientId,
                                        double balance,
                                        OffsetDateTime timestamp) {
//        var accountBalanceEvent = AccountBalanceEvent.builder()
//                .requestId(UUID.randomUUID().toString())
//                .clientId(clientId)
//                .balance(balance)
//                .timestamp(timestamp)
//                .build();
//
//        var senderRecord = create(topicName,
//                null,
//                timestamp.toInstant().toEpochMilli(),
//                (String) null,
//                accountBalanceEvent,
//                clientId);
//
//        return sender.send(just(senderRecord))
//                .doOnError(e -> log.error("balance event sending failed", e))
//                .doOnNext(r -> {
//                    log.debug("balance event sent: correlationMetadata {}, recordMetadata {}",
//                            r.correlationMetadata(),
//                            r.recordMetadata());
//                })
//                .next();

    }
}
