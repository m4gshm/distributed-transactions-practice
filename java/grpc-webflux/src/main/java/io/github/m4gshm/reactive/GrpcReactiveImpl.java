package io.github.m4gshm.reactive;

import grpcstarter.server.feature.exceptionhandling.GrpcExceptionResolver;
import io.github.m4gshm.GrpcConvertible;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.opentelemetry.context.Context.current;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class GrpcReactiveImpl implements GrpcReactive {

    MetadataFactory metadataFactory;
    StatusExtractor statusExtractor;
    List<GrpcExceptionResolver> grpcExceptionResolvers;
    Tracer tracer;

    private Throwable handle(Throwable throwable) {
        if (throwable instanceof GrpcConvertible grpcConvertible) {
            return grpcConvertible.toGrpcRuntimeException();
        } else if (throwable instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException;
        } else if (throwable instanceof StatusException statusException) {
            return statusException;
        }

        final var metadata = metadataFactory.newMetadata(throwable);
        for (var resolver : grpcExceptionResolvers) {
            var sre = resolver.resolve(throwable, null, metadata);
            if (sre != null) {
                return sre;
            }
        }
        return new StatusRuntimeException(statusExtractor.getStatus(throwable), metadata);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <P extends CorePublisher<T>, T> void subscribe(String name, StreamObserver<T> observer, P publisher) {
        var current = current();
        var span = tracer.spanBuilder("subscribe:" + name).setParent(current).startSpan();
        try (var _ = span.makeCurrent()) {
            final P p;
            if (publisher instanceof Mono<?> mono) {
                p = (P) mono.name(name);
            } else if (publisher instanceof Flux<?> flux) {
                p = (P) flux.name(name);
            } else {
                p = publisher;
            }
            p.subscribe(newSubscriber(name, span, observer));
        }
    }

    private <T> CoreSubscriber<? super T> newSubscriber(String name, Span span,
                                                        StreamObserver<T> observer) {
        return new CoreSubscriber<>() {
            volatile Throwable error;
//            volatile Span span;

            @Override
            public void onComplete() {
                try (var _ = span.makeCurrent()) {
                    log.trace("onComplete {}", name);
                    if (error == null) {
                        observer.onCompleted();
                    }
                    span.end();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                try (var _ = span.makeCurrent()) {
                    log.error("onError {}", name, throwable);
                    this.error = throwable;
                    span.recordException(throwable);
                    observer.onError(handle(throwable));
                }
            }

            @Override
            public void onNext(T t) {
                try (var _ = span.makeCurrent()) {
                    log.trace("onNext {}", name);
                    span.addEvent("onNext");
                    observer.onNext(t);
                }
            }

            @Override
            public void onSubscribe(Subscription s) {
//                var current = current();
//                this.span = tracer.spanBuilder("run:" + name).setParent(span).startSpan();
                try (var _ = span.makeCurrent()) {
                    log.trace("onSubscribe {}", name);
                    span.addEvent("onSubscribe");
                    s.request(1);
                }
            }
        };
    }
}
