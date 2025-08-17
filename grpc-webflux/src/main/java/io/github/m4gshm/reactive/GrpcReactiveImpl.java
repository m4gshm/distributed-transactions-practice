package io.github.m4gshm.reactive;

import grpcstarter.server.feature.exceptionhandling.GrpcExceptionResolver;
import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class GrpcReactiveImpl implements GrpcReactive {

    MetadataFactory metadataFactory;
    StatusExtractor statusExtractor;
    List<GrpcExceptionResolver> grpcExceptionResolvers;

    private Throwable handle(Throwable throwable) {
        final boolean grpcException;
        final Metadata metadata;
        if (throwable instanceof StatusRuntimeException statusRuntimeException) {
            grpcException = true;
            metadata = statusRuntimeException.getTrailers();
        } else if (throwable instanceof StatusException statusException) {
            grpcException = true;
            metadata = statusException.getTrailers();
        } else {
            grpcException = false;
            metadata = metadataFactory.newMetadata(throwable);
        }
        for (var resolver : grpcExceptionResolvers) {
            var sre = resolver.resolve(throwable, null, metadata);
            if (sre != null) {
                return sre;
            }
        }
        return grpcException ? throwable : new StatusRuntimeException(statusExtractor.getStatus(throwable), metadata);
    }

    @Override
    public <T> CoreSubscriber<T> newSubscriber(StreamObserver<T> observer) {
        return new CoreSubscriber<>() {

            volatile Throwable error;

            @Override
            public void onComplete() {
                if (error == null) {
                    observer.onCompleted();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error on subscribe", throwable);
                this.error = throwable;
                observer.onError(handle(throwable));
            }

            @Override
            public void onNext(T t) {
                observer.onNext(t);
            }

            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }
        };
    }

}
