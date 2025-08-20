package io.github.m4gshm.postgres.prepared.transaction;

import static io.github.m4gshm.postgres.prepared.transaction.Transaction.logTxId;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.just;

import java.time.OffsetDateTime;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class TwoPhaseTransaction {

    public static Mono<Void> commit(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("COMMIT PREPARED '" + id + "'")).then();
    }

    public static Mono<PreparedTransaction> getPreparedById(DSLContext dsl, String transactionId) {
        return Flux.from(dsl.resultQuery(
                "select transaction,gid,prepared from pg_prepared_xacts where database = current_database() and transaction = $1",
                transactionId)
        ).map(TwoPhaseTransaction::newPreparedTransaction).next();

    }

    public static Flux<PreparedTransaction> listPrepared(DSLContext dsl) {
        return Flux.from(dsl.resultQuery(
                "select transaction,gid,prepared from pg_prepared_xacts where database = current_database()"
        )).map(TwoPhaseTransaction::newPreparedTransaction);

    }

    private static PreparedTransaction newPreparedTransaction(org.jooq.Record r) {
        return PreparedTransaction.builder()
                .transaction(r.get(DSL.field("transaction ", Integer.class)))
                .gid(r.get(DSL.field("gid", String.class)))
                .prepared(r.get(DSL.field("prepared", OffsetDateTime.class)))
                .build();
    }

    public static Mono<Void> prepare(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("PREPARE TRANSACTION '" + id + "'")).then();
    }

    public static <T> Mono<T> prepare(DSLContext dsl, String id, Mono<T> routine) {
        return id != null ? routine.flatMap(result -> {
            return logTxId(dsl, "prepare2Pc", result);
        }).flatMap(result -> {
            return prepare(dsl, id).onErrorResume(throwable -> {
                return error(new PrepareTransactionException(id, throwable));
            }).thenReturn(result).switchIfEmpty(just(result));
        }) : routine;
    }

    public static Mono<Void> rollback(DSLContext dsl, @NonNull String id) {
        return from(dsl.query("ROLLBACK PREPARED '" + id + "'")).then();
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
