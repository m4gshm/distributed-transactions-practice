package io.github.m4gshm.jooq;

import org.jooq.DSLContext;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface ReactiveJooq {
    <T> Mono<T> inTransaction(String op, Function<DSLContext, Mono<T>> function);

    <T> Mono<T> newTransaction(String op, Function<DSLContext, Mono<T>> function);

    <T> Mono<T> outOfTransaction(String op, Function<DSLContext, Mono<T>> function);

    <T> Mono<T> supportTransaction(String op, Function<DSLContext, Mono<T>> function);
}
