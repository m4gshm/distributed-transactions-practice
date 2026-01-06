package io.github.m4gshm.postgres.prepared.transaction;

import io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransactionUtils.PrepareTransactionException;
import io.github.m4gshm.storage.ReactiveReadOperations;
import reactor.core.publisher.Mono;

import java.util.List;

import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

public interface ReactivePreparedTransactionService extends ReactiveReadOperations<PreparedTransaction, String> {
    Mono<Void> commit(String id);

    @Override
    Mono<List<PreparedTransaction>> findAll();

    @Override
    Mono<PreparedTransaction> findById(String id);

    Mono<Void> prepare(String id);

    default <T> Mono<T> prepare(String id, Mono<T> routine) {
        return id != null ? routine/*
                                    * .flatMap(result -> { return logTxId(dsl, "prepare2Pc", result); })
                                    */.flatMap(result -> {
            return prepare(id).onErrorResume(throwable -> {
                return error(new PrepareTransactionException(id, throwable));
            }).thenReturn(result).switchIfEmpty(just(result));
        }) : routine;
    }

    Mono<Void> rollback(String id);
}
