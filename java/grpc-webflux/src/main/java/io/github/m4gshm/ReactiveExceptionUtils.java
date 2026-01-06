package io.github.m4gshm;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.Collection;

@UtilityClass
public class ReactiveExceptionUtils {
    public static <S extends Enum<S>> Mono<Void> checkStatus(String opName,
                                                             String entity,
                                                             Object id,
                                                             S actual,
                                                             Collection<S> expected,
                                                             S intermediateStatus) {
        try {
            ExceptionUtils.checkStatus(opName, entity, id, actual, expected, intermediateStatus);
            return Mono.empty();
        } catch (UnsupportedOperationException e) {
            return Mono.error(e);
        }
    }
}
