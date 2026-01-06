package io.github.m4gshm.jooq;

import java.util.function.Function;

import org.jooq.DSLContext;

import reactor.core.publisher.Mono;

public interface ReactiveJooq {
    <T> Mono<T> inTransaction(Function<DSLContext, Mono<T>> function);

    <T> Mono<T> newTransaction(Function<DSLContext, Mono<T>> function);

    <T> Mono<T> outOfTransaction(Function<DSLContext, Mono<T>> function);
}
