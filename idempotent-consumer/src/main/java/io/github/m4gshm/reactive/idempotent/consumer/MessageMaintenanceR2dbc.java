package io.github.m4gshm.reactive.idempotent.consumer;

import io.github.m4gshm.reactive.idempotent.consumer.storage.tables.InputMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static reactor.core.publisher.Mono.from;

@Slf4j
@RequiredArgsConstructor
public class MessageMaintenanceR2dbc implements MessageMaintenanceService {
    private final MessageStorageR2dbc.DslEnv dslFactory;
    private final InputMessages table;

    @Override
    public Mono<Void> createTable() {
        return dslFactory.provide(dsl -> {
            var primaryKey = table.getPrimaryKey();
            var ddl = dsl
                         .createTableIfNotExists(table)
                         .columns(table.fields());
            if (primaryKey != null) {
                ddl = ddl.constraints(primaryKey.constraint());
            }
            return from(ddl).then();
        }).doOnError(t -> {
            log.error("createTable error: {}", table.getQualifiedName(), t);
        }).doOnSuccess(_ -> {
            log.trace("createTable success: {}", table.getQualifiedName());
        });
    }
}
