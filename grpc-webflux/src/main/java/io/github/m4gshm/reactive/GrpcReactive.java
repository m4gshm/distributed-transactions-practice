package io.github.m4gshm.reactive;

import io.grpc.stub.StreamObserver;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;

public interface GrpcReactive {
    <T> CoreSubscriber<T> newSubscriber(StreamObserver<T> observer);

    default <P extends CorePublisher<T>, T> void subscribe(StreamObserver<T> observer, P publisher) {
        publisher.subscribe(newSubscriber(observer));
    }
}
