package io.github.m4gshm.storage;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

public interface ReactiveCrudStorage<T, ID> extends ReactiveReadOperations<T, ID> {

    Mono<T> save(@Valid T order);

}
