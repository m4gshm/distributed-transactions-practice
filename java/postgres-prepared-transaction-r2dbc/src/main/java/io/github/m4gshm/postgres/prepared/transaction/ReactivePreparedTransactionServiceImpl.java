package io.github.m4gshm.postgres.prepared.transaction;

import io.github.m4gshm.jooq.ReactiveJooq;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.github.m4gshm.postgres.prepared.transaction.ReactiveTwoPhaseTransactionUtils.getPreparedById;
import static io.github.m4gshm.postgres.prepared.transaction.ReactiveTwoPhaseTransactionUtils.listPrepared;
import static io.github.m4gshm.r2dbc.postgres.PostgresqlExceptionUtils.getPostgresqlException;
import static io.github.m4gshm.storage.NotFoundException.newNotFoundException;

@Slf4j
@RequiredArgsConstructor
public class ReactivePreparedTransactionServiceImpl implements ReactivePreparedTransactionService {
    @Getter
    private final Class<PreparedTransaction> entityClass = PreparedTransaction.class;

    private final ReactiveJooq jooq;

    private static Throwable ifNotExist(Object id, Throwable e) {
        var postgresqlException = getPostgresqlException(e);
        var notFound = postgresqlException != null && "42704".equals(postgresqlException.getErrorDetails().getCode());
        return notFound ? newNotFoundException(e, PreparedTransaction.class, id) : e;
    }

    private static <T> Mono<T> notFoundable(Object id, Mono<T> mono) {
        return mono.onErrorMap(e -> ifNotExist(id, e));
    }

    @Override
    public Mono<Void> commit(String id) {
        return jooq.outOfTransaction("commit",
                dsl -> notFoundable(id,
                        ReactiveTwoPhaseTransactionUtils.commit(dsl, id)));
    }

    @Override
    public Mono<List<PreparedTransaction>> findAll() {
        return jooq.supportTransaction("findAll", dsl -> listPrepared(dsl).collectList());
    }

    @Override
    public Mono<PreparedTransaction> findById(String id) {
        return jooq.supportTransaction("findById", dsl -> notFoundable(id, getPreparedById(dsl, id)));
    }

    @Override
    public Mono<Void> prepare(String id) {
        return jooq.inTransaction("prepare", dsl -> ReactiveTwoPhaseTransactionUtils.prepare(dsl, id));
    }

    @Override
    public Mono<Void> rollback(String id) {
        return jooq.outOfTransaction("rollback",
                dsl -> notFoundable(id,
                        ReactiveTwoPhaseTransactionUtils.rollback(dsl, id)));
    }
}
