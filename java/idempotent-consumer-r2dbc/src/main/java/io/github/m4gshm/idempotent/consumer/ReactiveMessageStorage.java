package io.github.m4gshm.idempotent.consumer;

import reactor.core.publisher.Mono;

public interface ReactiveMessageStorage {

    Mono<Void> storeUnique(Message message);
}
