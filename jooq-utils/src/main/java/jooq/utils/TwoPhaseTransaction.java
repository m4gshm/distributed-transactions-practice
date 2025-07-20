package jooq.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import reactor.core.publisher.Mono;

@UtilityClass
public class TwoPhaseTransaction {
    public static <T> Mono<T> prepare(Mono<T> routine, DSLContext dsl, String id) {
        return routine.flatMap(element -> {
            return prepare(dsl, id).thenReturn(element).switchIfEmpty(Mono.fromSupplier(() -> {
                return element;
            }));
        });
    }

    public static Mono<Integer> prepare(DSLContext dsl, @NonNull String id) {
        return Mono.from(dsl.query("PREPARE TRANSACTION '" + id + "'"));
    }

    public static Mono<Integer> commit(DSLContext dsl, @NonNull String id) {
        return Mono.from(dsl.query("COMMIT PREPARED '" + id + "'"));
    }

    public static Mono<Integer> rollback(DSLContext dsl, @NonNull String id) {
        return Mono.from(dsl.query("ROLLBACK PREPARED '" + id + "'"));
    }
}
