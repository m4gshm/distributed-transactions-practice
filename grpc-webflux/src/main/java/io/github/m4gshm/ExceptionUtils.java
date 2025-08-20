package io.github.m4gshm;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.Collection;

import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;

@UtilityClass
public class ExceptionUtils {
    public static <S extends Enum<S>> Mono<Void> checkStatus(S actual, Collection<S> expected, S intermediateStatus) {
        return !(expected.contains(actual) || (intermediateStatus != null && intermediateStatus == actual))
                ? error(newStatusException(actual.name(), "inappropriate status " + actual + ", expected " + expected))
                : empty();
    }

    public static UnexpectedEntityStatusException newStatusException(String actual, String message) {
        return new UnexpectedEntityStatusException(actual, message);
    }

    public static InvalidStateException newValidateException(String message) {
        return new InvalidStateException(message);
    }

}
