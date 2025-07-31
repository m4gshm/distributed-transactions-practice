package io.github.m4gshm.jooq.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import reactor.core.publisher.Mono;

import static io.github.m4gshm.jooq.utils.Transaction.logTxId;
import static reactor.core.publisher.Mono.*;

@UtilityClass
public class TwoPhaseTransaction {

    public static <T> Mono<T> prepare(boolean enabled, DSLContext dsl, String id, Mono<T> routine) {
        return enabled ? routine.flatMap(result -> {
            return logTxId(dsl, "prepare2Pc", result);
        }).flatMap(result -> {
            return prepare(dsl, id).onErrorResume(throwable -> error(new PrepareTransactionException(id, throwable)))
                    .thenReturn(result).switchIfEmpty(just(result));
        }) : routine;
    }

    public static Mono<Integer> prepare(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("PREPARE TRANSACTION '" + id + "'"));
    }

    public static Mono<Integer> commit(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("COMMIT PREPARED '" + id + "'"));
    }

    public static Mono<Integer> rollback(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("ROLLBACK PREPARED '" + id + "'"));
    }

    public static class PrepareTransactionException extends RuntimeException {

        public final String id;

        public PrepareTransactionException(String id, Throwable throwable) {
            super(id, throwable);
            this.id = id;
        }
    }
}
