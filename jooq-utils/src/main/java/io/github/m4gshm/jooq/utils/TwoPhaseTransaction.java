package io.github.m4gshm.jooq.utils;

import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

import static io.github.m4gshm.jooq.utils.Transaction.logTxId;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.just;

@UtilityClass
public class TwoPhaseTransaction {

    public static Mono<Integer> commit(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("COMMIT PREPARED '" + id + "'"));
    }

    public static Flux<PreparedTransaction> listPrepared(DSLContext dsl) {
        return Flux.from(dsl.resultQuery(
                "select transaction,gid,prepared from pg_prepared_xacts where database = current_database()"))
                .map(r -> {
                    return PreparedTransaction.builder()
                            .transaction(r.get(DSL.field("transaction ", Integer.class)))
                            .gid(r.get(DSL.field("gid", String.class)))
                            .prepared(r.get(DSL.field("prepared", OffsetDateTime.class)))
                            .build();
                });

    }

    public static Mono<Integer> prepare(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("PREPARE TRANSACTION '" + id + "'"));
    }

    public static <T> Mono<T> prepare(boolean enabled, DSLContext dsl, String id, Mono<T> routine) {
        return enabled ? routine.flatMap(result -> {
            return logTxId(dsl, "prepare2Pc", result);
        }).flatMap(result -> {
            return prepare(dsl, id).onErrorResume(throwable -> {
                return error(new PrepareTransactionException(id, throwable));
            }).thenReturn(result).switchIfEmpty(just(result));
        }) : routine;
    }

    public static Mono<Integer> rollback(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("ROLLBACK PREPARED '" + id + "'"));
    }

    @Builder
    public record PreparedTransaction(Integer transaction, String gid, OffsetDateTime prepared) {

    }

    public static class PrepareTransactionException extends RuntimeException {

        public final String id;

        public PrepareTransactionException(String id,
                Throwable throwable) {
            super(id, throwable);
            this.id = id;
        }
    }
}
