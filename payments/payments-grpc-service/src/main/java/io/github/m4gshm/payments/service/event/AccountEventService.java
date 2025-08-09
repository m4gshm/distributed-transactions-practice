package io.github.m4gshm.payments.service.event;

import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;

public interface AccountEventService {
    Mono<SenderResult<Void>> sendAccountBalanceEvent(String clientId, double balance);
}
