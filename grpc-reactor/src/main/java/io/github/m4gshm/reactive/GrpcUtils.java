package io.github.m4gshm.reactive;

import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.INTERNAL;

@Slf4j
@UtilityClass
public class GrpcUtils {
    public static <T> CoreSubscriber<T> newSubscriber(StreamObserver<T> observer) {
        return new CoreSubscriber<>() {

            volatile Throwable error;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(T t) {
                observer.onNext(t);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error on subscribe", throwable);
                var trailers = new Metadata();
                this.error = throwable;
                try {
                    putNotNull(trailers, "message", throwable.getMessage());
                    putNotNull(trailers, "type", throwable.getClass().getName());
                } catch (Exception e) {
                    log.error("Error on subscribe errorHandling", e);
                }
                observer.onError(new StatusException(INTERNAL, trailers));
            }

            @Override
            public void onComplete() {
                if (error == null) {
                    observer.onCompleted();
                }
            }
        };
    }

    private static void putNotNull(Metadata trailers, String name, String value) {
        if (value != null) {
            trailers.put(Metadata.Key.of(name, ASCII_STRING_MARSHALLER), value);
        }
    }

    public static <T> void subscribe(StreamObserver<T> observer, Mono<T> mono) {
        mono.subscribe(newSubscriber(observer));
    }

    public static <T> void subscribe(StreamObserver<T> observer, Flux<T> flux) {
        flux.subscribe(newSubscriber(observer));
    }
}
