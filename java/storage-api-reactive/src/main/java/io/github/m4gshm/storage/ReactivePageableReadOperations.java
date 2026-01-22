package io.github.m4gshm.storage;

import reactor.core.publisher.Mono;

import java.util.List;

public interface ReactivePageableReadOperations<T, ID> {
    Mono<List<T>> findAll(Page page);

}
