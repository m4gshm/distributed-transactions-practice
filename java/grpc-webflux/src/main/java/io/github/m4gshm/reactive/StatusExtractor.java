package io.github.m4gshm.reactive;

import io.grpc.Status;

public interface StatusExtractor {
    Status getStatus(Throwable throwable);
}
