package io.github.m4gshm.reactive.idempotent.consumer;

import reactor.core.publisher.Mono;

public interface MessageStorage {

    Mono<Void> storeUnique(Message message);
}
