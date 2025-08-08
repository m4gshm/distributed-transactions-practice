package io.github.m4gshm.payments.service.integration;

import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;

public interface AccountEventService {
    Mono<SenderResult<Void>> sendAccountTopUp(String clientId);
}
