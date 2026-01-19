package io.github.m4gshm;

import io.grpc.Metadata;

public interface MetadataFactory {
    Metadata newMetadata(Throwable throwable);
}
