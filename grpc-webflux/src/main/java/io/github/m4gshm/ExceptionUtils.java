package io.github.m4gshm;

import io.grpc.Status;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.Collection;

import static io.grpc.Status.FAILED_PRECONDITION;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;

@UtilityClass
public class ExceptionUtils {
    public static <S extends Enum<S>> Mono<Void> checkStatus(S actual, Collection<S> expected) {
        return !expected.contains(actual)
                                          ? error(newStatusException(FAILED_PRECONDITION,
                                                                     "inappropriate status " + actual
                                                                                          +
                                                                                          ", expected "
                                                                                          + expected))
                                          : empty();
    }

    public static InternalStatusException newStatusException(Status status, String message) {
        return new InternalStatusException(status, message);
    }

}
