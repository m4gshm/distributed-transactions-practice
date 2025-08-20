/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package io.github.m4gshm;

import io.grpc.StatusRuntimeException;

public interface GrpcConvertible {
    StatusRuntimeException toGrpcRuntimeException();
}
