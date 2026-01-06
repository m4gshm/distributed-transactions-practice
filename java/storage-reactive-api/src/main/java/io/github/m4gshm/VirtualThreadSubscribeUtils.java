package io.github.m4gshm;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

import static reactor.core.scheduler.Schedulers.boundedElastic;

@UtilityClass
public class VirtualThreadSubscribeUtils {
    public static Mono<Void> subscribe(Runnable runnable) {
        return Mono.<Void>fromRunnable(runnable).subscribeOn(boundedElastic());
    }

    public static <T> Mono<T> subscribe(Supplier<T> supplier) {
        return Mono.fromSupplier(supplier).subscribeOn(boundedElastic());
    }
}
