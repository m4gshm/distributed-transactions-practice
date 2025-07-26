package io.github.m4gshm.storage;

import reactor.core.publisher.Mono;

public interface CrudStorage<T, ID> extends ReadStorage<T, ID> {

    Mono<T> save(T order);

}
