package io.github.m4gshm;

import io.grpc.StatusRuntimeException;

public interface GrpcConvertible {
    StatusRuntimeException toGrpcRuntimeException();
}
