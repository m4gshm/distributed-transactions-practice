package io.github.m4gshm.reactive.idempotent.consumer;

import reactor.core.publisher.Mono;

public interface MessageMaintenanceService {
    Mono<Void> createTable();
}
