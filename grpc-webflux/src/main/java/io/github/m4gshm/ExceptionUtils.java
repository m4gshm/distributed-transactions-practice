package io.github.m4gshm;

import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;

import java.util.Collection;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

@UtilityClass
public class ExceptionUtils {
    public static <S extends Enum<S>> Mono<Void> checkStatus(String opName,
                                                             S actual,
                                                             Collection<S> expected,
                                                             S intermediateStatus) {
        return !(expected.contains(actual) || (intermediateStatus != null && intermediateStatus == actual))
                ? error(newStatusException(actual.name(),
                        "inappropriate status " + actual
                                + ", expected "
                                + expected
                                + (opName != null && !opName.isBlank() ? ", operation " + opName : "")))
                : empty();
    }

    public static UnexpectedEntityStatusException newStatusException(String actual, String message) {
        return new UnexpectedEntityStatusException(actual, message);
    }

    public static InvalidStateException newValidateException(String message) {
        return new InvalidStateException(message);
    }

}
