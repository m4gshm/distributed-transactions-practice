package io.github.m4gshm.reactive;

import io.github.m4gshm.GrpcExceptionConverter;
import io.github.m4gshm.tracing.TraceService;
import io.grpc.stub.StreamObserver;
import io.micrometer.observation.Observation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class GrpcReactiveImpl implements GrpcReactive {

    GrpcExceptionConverter grpcExceptionConverter;
    TraceService traceService;

    private <T, SP, SC extends AutoCloseable> CoreSubscriber<? super T> newSubscriber(
            String name,
            SP span,
            BiFunction<SP,
                    String,
                    SC> setAsLocalEvent,
            Consumer<SP> stop,
            BiConsumer<SP,
                    Throwable> errorHandler,
            StreamObserver<T> observer
    ) {
        return new CoreSubscriber<>() {
            volatile Throwable error;

            @Override
            @SneakyThrows
            public void onComplete() {
                try (var _ = setAsLocalEvent.apply(span, "onComplete")) {
                    log.trace("onComplete {}", name);
                    if (error == null) {
                        observer.onCompleted();
                    }
                } finally {
                    stop.accept(span);
                }
            }

            @Override
            @SneakyThrows
            public void onError(Throwable throwable) {
                try (var _ = setAsLocalEvent.apply(span, "onError")) {
                    log.error("onError {}", name, throwable);
                    this.error = throwable;
                    observer.onError(grpcExceptionConverter.convertToGrpcStatusException(throwable));
                } finally {
                    errorHandler.accept(span, throwable);
                    stop.accept(span);
                }
            }

            @Override
            @SneakyThrows
            public void onNext(T t) {
                try (var _ = setAsLocalEvent.apply(span, "onNext")) {
                    log.trace("onNext {}", name);
                    observer.onNext(t);
                }
            }

            @Override
            @SneakyThrows
            public void onSubscribe(Subscription s) {
                try (var _ = setAsLocalEvent.apply(span, "onSubscribe")) {
                    log.trace("onSubscribe {}", name);
                    s.request(1);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public <P extends CorePublisher<T>, T> void subscribe(
            String name,
            StreamObserver<T> observer,
            Supplier<P> publisherFactory
    ) {
        var spanName = "subscribe:" + name;
        var trace = /* startNewSpan(spanName); */traceService.startNewObservation(spanName);
        try (var _ = traceService.startLocal(trace)) {
            var publisher = publisherFactory.get();
            final P p;
            if (publisher instanceof Mono<?> mono) {
                p = (P) mono.name(name)
                        .contextWrite(context -> traceService.putToReactContext(context, trace));
            } else if (publisher instanceof Flux<?> flux) {
                p = (P) flux.name(name)
                        .contextWrite(context -> traceService.putToReactContext(context, trace));
            } else {
                p = publisher;
            }
//            p.subscribe(newSubscriber(name, trace, this::setAsLocalEvent, Span::end,
//                    Span::recordException, observer));
            p.subscribe(newSubscriber(name,
                    trace,
                    traceService::startLocalEvent,
                    Observation::stop,
                    Observation::error,
                    observer));
        }
    }
}
