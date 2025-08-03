package io.github.m4gshm;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.Collection;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.FAILED_PRECONDITION;
import static reactor.core.publisher.Mono.*;

@UtilityClass
public class ExceptionUtils {
    public static StatusRuntimeException newStatusRuntimeException(Status status, String message) {
        var metadata = new Metadata();
        metadata.put(Key.of("message", ASCII_STRING_MARSHALLER), message);
        return new StatusRuntimeException(status, metadata);
    }

    public static <T, S extends Enum<S>> Mono<T> checkStatus(S actual, Collection<S> expected, Mono<T> next) {
        return !expected.contains(actual)
                ? error(newStatusRuntimeException(FAILED_PRECONDITION, "inappropriate status: " + expected))
                : next;
    }

}
