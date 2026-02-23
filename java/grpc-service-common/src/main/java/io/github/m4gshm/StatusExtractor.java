package io.github.m4gshm;

import io.grpc.Status;

public interface StatusExtractor {
    Status getStatus(Throwable throwable);
}
