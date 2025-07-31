package io.github.m4gshm;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.experimental.UtilityClass;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

@UtilityClass
public class ExceptionUtils {
    public static StatusRuntimeException newStatusException(Status status, String message) {
        var metadata = new Metadata();
        metadata.put(Metadata.Key.of("message", ASCII_STRING_MARSHALLER), message);
        return new StatusRuntimeException(status, metadata);
    }
}
