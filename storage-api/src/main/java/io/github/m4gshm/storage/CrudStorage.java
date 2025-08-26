package io.github.m4gshm.storage;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

public interface CrudStorage<T, ID> extends ReadOperations<T, ID> {

    Mono<T> save(@Valid T order);

}
