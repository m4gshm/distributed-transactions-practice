package io.github.m4gshm.postgres.prepared.transaction;

import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.error;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@UtilityClass
public class ReactiveTwoPhaseTransactionUtils {

    private static Mono<Void> checkTransactionId(String transactionId, Mono<Void> operation) {
        return defer(() -> transactionId.isBlank()
                ? error(new IllegalArgumentException("transactionId cannot be blank"))
                : operation);
    }

    public static Mono<Void> commit(DSLContext dsl, @NonNull String transactionId) {
        return checkTransactionId(transactionId,
                Mono.from(TwoPhaseTransactionUtils.commit(dsl, transactionId)).then());
    }

    public static Mono<PreparedTransaction> getPreparedById(DSLContext dsl,
                                                            String transactionId) {
        return Flux.from(TwoPhaseTransactionUtils.getPreparedById(dsl, transactionId))
                .map(TwoPhaseTransactionUtils::newPreparedTransaction)
                .next();
    }

    public static Flux<PreparedTransaction> listPrepared(DSLContext dsl) {
        return Flux.from(TwoPhaseTransactionUtils.listPrepared(dsl))
                .map(TwoPhaseTransactionUtils::newPreparedTransaction);

    }

    public static Mono<Void> prepare(DSLContext dsl, @NonNull String transactionId) {
        return checkTransactionId(transactionId,
                Mono.from(TwoPhaseTransactionUtils.prepare(dsl, transactionId)).then());
    }

    public static Mono<Void> rollback(DSLContext dsl,
                                      @NonNull String transactionId) {
        return checkTransactionId(transactionId,
                Mono.from(TwoPhaseTransactionUtils.rollback(dsl, transactionId)).then());
    }

}
