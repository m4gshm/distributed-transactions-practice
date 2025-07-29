package io.github.m4gshm.jooq.utils;

import io.github.m4gshm.storage.ReadStorage;
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
    public static <ID, T> Function<Integer, Mono<T>> checkUpdate(String entity, ID id, Supplier<T> result) {
        return count -> {
            log.debug("update result count: entity [{}], id [{}], rows [{}]", entity, id, count);
            return count > 0
                    ? fromSupplier(result)
                    : error(new ReadStorage.NotFoundException("zero updated count on " + entity + " " + id));
        };
    }
}
