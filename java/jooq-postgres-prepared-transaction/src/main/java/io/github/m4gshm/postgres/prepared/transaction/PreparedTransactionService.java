package io.github.m4gshm.postgres.prepared.transaction;

import io.github.m4gshm.storage.ReadOperations;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PreparedTransactionService extends ReadOperations<PreparedTransaction, String> {
    Mono<Void> commit(String id);

    @Override
    Mono<List<PreparedTransaction>> findAll();

    @Override
    Mono<PreparedTransaction> findById(String id);

    Mono<Void> prepare(String id);

    Mono<Void> rollback(String id);
}
