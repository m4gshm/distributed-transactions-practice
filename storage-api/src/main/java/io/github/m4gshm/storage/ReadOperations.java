package io.github.m4gshm.storage;

import reactor.core.publisher.Mono;

import java.util.List;

import static reactor.core.publisher.Mono.error;

public interface ReadOperations<T, ID> {
    Mono<List<T>> findAll();

    Mono<T> findById(ID id);

    default Mono<T> getById(ID id) {
        return findById(id).switchIfEmpty(error(() -> {
            return new NotFoundException(getEntityClass(), id);
        }));
    }

    Class<T> getEntityClass();

}
