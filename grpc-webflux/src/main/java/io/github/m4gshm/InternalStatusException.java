package io.github.m4gshm;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.Getter;

@Getter
public class InternalStatusException extends RuntimeException {
    private final Status status;

    public InternalStatusException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public StatusRuntimeException toGrpcRuntimeException() {
        var metadata = new Metadata();
        metadata.put(Metadata.Key.of("message", ASCII_STRING_MARSHALLER), getMessage());
        var statusRuntimeException = new StatusRuntimeException(status, metadata);
        statusRuntimeException.initCause(this);
        return statusRuntimeException;
    }
}
