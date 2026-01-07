package io.github.m4gshm.reactive;

import io.github.m4gshm.InvalidStateException;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.function.BiConsumer;

import static reactor.core.publisher.Mono.deferContextual;

@Slf4j
@UtilityClass
public class ReactiveUtils {
    private static Observation getObservation(ContextView context) {
        return context.hasKey(ObservationThreadLocalAccessor.KEY) && context.get(
                ObservationThreadLocalAccessor.KEY) instanceof Observation o
                        ? o
                        : Observation.NOOP;
    }

    public static <T, R> Mono<R> toMono(
                                        String operationName,
                                        T request,
                                        BiConsumer<T, StreamObserver<R>> call
    ) {
        return deferContextual(context -> {
            return Mono.<R>create(sink -> {
                log.trace("call {}", operationName);
                final var observation = getObservation(context);
                try (var _ = observation.openScope()) {
                    call.accept(request, toStreamObserver(sink));
                }
            })
                    .doOnError(e -> log.error("error on {}", operationName, e))
                    .name(operationName);
        });
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
