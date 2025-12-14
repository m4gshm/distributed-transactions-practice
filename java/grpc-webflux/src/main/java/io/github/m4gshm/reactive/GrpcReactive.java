package io.github.m4gshm.reactive;

import io.grpc.stub.StreamObserver;
import reactor.core.CorePublisher;

public interface GrpcReactive {
    <P extends CorePublisher<T>, T> void subscribe(String name, StreamObserver<T> observer, P publisher);
}
