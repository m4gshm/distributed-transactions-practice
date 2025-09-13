package io.github.m4gshm.storage;

import static reactor.core.publisher.Mono.error;

import java.util.List;

import reactor.core.publisher.Mono;

public interface ReadOperations<T, ID> {
    Mono<List<T>> findAll();

    Mono<T> findById(ID id);

    default Mono<T> getById(ID id) {
        return findById(id).switchIfEmpty(error(() -> {
            return NotFoundException.newNotFoundException(getEntityClass(), id);
        }));
    }

    Class<T> getEntityClass();

}
