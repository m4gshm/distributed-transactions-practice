package io.github.m4gshm.storage;

import lombok.Getter;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static reactor.core.publisher.Mono.error;

public interface ReadStorage<T, ID> {
    Class<T> getEntityClass();

    Mono<List<T>> findAll();

    Mono<T> findById(ID id);

    default Mono<T> getById(ID id) {
        return findById(id).switchIfEmpty(error(() -> {
            return new NotFoundException(getEntityClass(), id);
        }));
    }

    @Getter
    class NotFoundException extends RuntimeException {
        private final Object[] keys;

        public NotFoundException(Class<?> entityType, Object... keys) {
            this(entityType.getName() + " not found by " + Arrays.toString(keys), keys);
        }

        public NotFoundException(String message, Object... keys) {
            super(message);
            this.keys = keys;
        }
    }
}
