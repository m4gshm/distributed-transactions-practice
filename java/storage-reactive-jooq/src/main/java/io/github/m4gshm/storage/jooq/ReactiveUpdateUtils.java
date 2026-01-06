package io.github.m4gshm.storage.jooq;

import static io.github.m4gshm.storage.NotFoundException.newNotFoundException;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.fromSupplier;

import java.util.function.Function;
import java.util.function.Supplier;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@UtilityClass
public class ReactiveUpdateUtils {
    public static <ID, T> Function<Integer, Mono<T>> checkUpdateCount(String entity, ID id, Supplier<T> result) {
        return count -> {
            log.debug("update result count: entity [{}], id [{}], rows [{}]", entity, id, count);
            if (count > 0) {
                return fromSupplier(result);
            }
            return notFound(entity, id);
        };
    }

    public static <ID, T> Mono<T> notFound(String entity, ID id) {
        return error(newNotFoundException("zero updated count on " + entity + " " + id, entity, id));
    }
}
