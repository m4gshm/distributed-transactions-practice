package io.github.m4gshm.storage.jooq;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.function.Function;
import java.util.function.Supplier;

import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.fromSupplier;

@Slf4j
@UtilityClass
public class Update {
    public static <ID, T> Function<Integer, Mono<T>> checkUpdateCount(String entity, ID id, Supplier<T> result) {
        return count -> {
            log.debug("update result count: entity [{}], id [{}], rows [{}]", entity, id, count);
            return count > 0
                    ? fromSupplier(result)
                    : notFound(entity, id);
        };
    }

    public static <ID, T> Mono<T> notFound(String entity, ID id) {
        return error(new io.github.m4gshm.storage.NotFoundException("zero updated count on " + entity + " " + id));
    }
}
