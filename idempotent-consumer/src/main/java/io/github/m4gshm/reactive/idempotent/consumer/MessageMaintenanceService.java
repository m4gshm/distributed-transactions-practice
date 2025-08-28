package io.github.m4gshm.reactive.idempotent.consumer;

import java.time.LocalDate;

import reactor.core.publisher.Mono;

public interface MessageMaintenanceService {
    Mono<Void> addPartition(LocalDate moment, Partition partition);

    Mono<Void> createTable(boolean usePartition);

    enum Partition {
            CURRENT, NEXT;
    }
}
