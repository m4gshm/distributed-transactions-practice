package io.github.m4gshm.orders.service;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static io.github.m4gshm.orders.service.OrderServiceUtils.getCurrentStatusFromError;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

@Slf4j
@UtilityClass
public class OrderServiceReactiveUtils {

    static <T> Function<Throwable, Mono<T>> statusError(Function<String, T> converter) {
        return e -> {
            var status = getCurrentStatusFromError(converter, e);
            if (status != null) {
                log.debug("already in status {}", status);
                return just(status);
            }
            return error(e);
        };
    }

}
