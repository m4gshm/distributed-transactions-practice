package io.github.m4gshm.reactive;

import io.grpc.Metadata;

public interface MetadataFactory {
    Metadata newMetadata(Throwable throwable);
}
