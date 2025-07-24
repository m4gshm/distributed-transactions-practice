package io.github.m4gshm.reactive;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.INTERNAL;
import static io.grpc.Status.NOT_FOUND;
import static io.grpc.Status.PERMISSION_DENIED;
import static io.grpc.Status.UNAUTHENTICATED;

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
                observer.onError(new StatusException(getStatus(throwable), trailers));
            }

            @Override
            public void onComplete() {
                if (error == null) {
                    observer.onCompleted();
                }
            }
        };
    }

    private static Status getStatus(Throwable throwable) {
        if (throwable instanceof StatusException statusException) {
            return statusException.getStatus();
        } else if (throwable instanceof ErrorResponse errorResponse) {
            var statusCode = errorResponse.getStatusCode();
            if (statusCode.is4xxClientError()) {
                var httpStatus = HttpStatus.resolve(statusCode.value());
                return toGrpcStatus(httpStatus);
            }
        } else {
            var responseStatus = throwable.getClass().getAnnotation(ResponseStatus.class);
            if (responseStatus != null) {
                return toGrpcStatus(responseStatus.value());
            }
        }
        return INTERNAL;
    }

    private static Status toGrpcStatus(HttpStatus httpStatus) {
        return switch (httpStatus) {
            case UNAUTHORIZED -> UNAUTHENTICATED;
            case FORBIDDEN -> PERMISSION_DENIED;
            case NOT_FOUND -> NOT_FOUND;
            case REQUEST_TIMEOUT -> Status.DEADLINE_EXCEEDED;
            default -> INTERNAL;
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
