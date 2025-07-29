package io.github.m4gshm.jooq.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import reactor.core.publisher.Mono;

import static io.github.m4gshm.jooq.utils.Transaction.logTxId;
import static reactor.core.publisher.Mono.fromSupplier;

@UtilityClass
public class TwoPhaseTransaction {

    public static <T> Mono<T> prepare(boolean enabled, DSLContext dsl, String id, Mono<T> routine) {
        return enabled ? prepare(dsl, id, routine.flatMap(l -> {
            return logTxId(dsl, "prepare2Pc", l);
        })) : routine;
    }

    public static <T> Mono<T> prepare(DSLContext dsl, String id, Mono<T> routine) {
        return routine.flatMap(element -> {
            return prepare(dsl, id).thenReturn(element).switchIfEmpty(fromSupplier(() -> {
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
