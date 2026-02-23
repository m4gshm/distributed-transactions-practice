package io.github.m4gshm.payments.service.event;

import java.time.OffsetDateTime;

import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;

public interface ReactiveAccountEventService {
    Mono<SenderResult<String>> sendAccountBalanceEvent(String clientId, double balance, OffsetDateTime timestamp);
}
