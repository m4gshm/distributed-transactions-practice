package io.github.m4gshm.jooq;

import org.jooq.DSLContext;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface Jooq {
    <T> Mono<T> transactional(Function<DSLContext, Mono<T>> function);
}
