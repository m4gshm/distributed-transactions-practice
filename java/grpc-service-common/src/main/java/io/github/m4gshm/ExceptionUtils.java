package io.github.m4gshm;

import lombok.experimental.UtilityClass;

import java.util.Collection;

@UtilityClass
public class ExceptionUtils {
    public static <S extends Enum<S>> void checkStatus(String opName,
                                                       String entity,
                                                       Object id,
                                                       S actual,
                                                       Collection<S> expected,
                                                       S intermediateStatus
    ) {
        if (!(expected.contains(actual) || (intermediateStatus != null && intermediateStatus == actual))) {
            var operation = opName != null && !opName.isBlank() ? ", operation " + opName : "";
            var entityMsg = entity != null && !entity.isBlank() ? ", " + entity + " " + id : "";
            throw newStatusException(actual.name(),
                    "inappropriate status " + actual
                            + ", expected "
                            + expected
                            + operation
                            + entityMsg
            );
        }

    }

    public static UnexpectedEntityStatusException newStatusException(String actual, String message) {
        return new UnexpectedEntityStatusException(actual, message);
    }

    public static InvalidStateException newValidateException(String message) {
        return new InvalidStateException(message);
    }

}
