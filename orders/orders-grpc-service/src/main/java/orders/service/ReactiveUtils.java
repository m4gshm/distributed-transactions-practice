package orders.service;

import io.grpc.stub.StreamObserver;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.function.BiConsumer;

@UtilityClass
public class ReactiveUtils {
    public static <T> StreamObserver<T> toStreamObserver(MonoSink<T> sink) {
        return new StreamObserver<>() {
            @Override
            public void onNext(T value) {
                sink.success(value);
            }

            @Override
            public void onError(Throwable t) {
                sink.error(t);
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    public static <T, R> Mono<R> toMono(T request, BiConsumer<T, StreamObserver<R>> call) {
        return Mono.create(sink -> call.accept(request, toStreamObserver(sink)));
    }

}
