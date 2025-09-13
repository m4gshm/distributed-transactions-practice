package io.github.m4gshm.reactive;

import io.github.m4gshm.InvalidStateException;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.context.Context;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.List;
import java.util.function.BiConsumer;

import static io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator.runWithContext;
import static reactor.core.scheduler.Schedulers.immediate;

@Slf4j
@UtilityClass
public class ReactiveUtils {
    public static <T, R> Mono<R> toMono(String operationName, T request, BiConsumer<T, StreamObserver<R>> call) {
        var context = Context.current();
        var mono = Mono.<R>create(sink -> {
            log.trace("call {}", operationName);
            call.accept(request, toStreamObserver(sink));
        }).doOnError(e -> log.error("error on {}", operationName, e));
        return runWithContext(mono, context).subscribeOn(immediate());
    }

    public static <T> StreamObserver<T> toStreamObserver(MonoSink<T> sink) {
        return new StreamObserver<>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof InvalidStateException statusException) {
                    sink.error(statusException.toGrpcRuntimeException());
                } else {
                    if (t instanceof StatusException || t instanceof StatusRuntimeException) {
                        sink.error(t);
                    } else {
                        log.error(
                                "expected one of {} but caught {}",
                                List.of(StatusException.class, StatusRuntimeException.class),
                                t != null
                                        ? t.getClass()
                                        : null,
                                t
                        );
                    }
                }
            }

            @Override
            public void onNext(T value) {
                sink.success(value);
            }
        };
    }

}
