package io.github.m4gshm;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.Getter;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.FAILED_PRECONDITION;

@Getter
public class InvalidStateException extends RuntimeException {

    public static final Key<String> MESSAGE = key("message");
    public static final Key<String> TYPE = key("type");

    private final Status grpcStatus = FAILED_PRECONDITION;

    private static Key<String> key(String message) {
        return Key.of(message, ASCII_STRING_MARSHALLER);
    }

    public InvalidStateException(String message) {
        super(message);
    }

    protected Metadata newMetadata() {
        var metadata = new Metadata();
        metadata.put(MESSAGE, getMessage());
        metadata.put(TYPE, this.getClass().getSimpleName());
        return metadata;
    }

    public StatusRuntimeException toGrpcRuntimeException() {
        var metadata = newMetadata();
        var statusRuntimeException = new StatusRuntimeException(grpcStatus, metadata);
        statusRuntimeException.initCause(this);
        return statusRuntimeException;
    }
}
