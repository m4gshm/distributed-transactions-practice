package io.github.m4gshm.postgres.prepared.transaction;

import java.util.List;

import io.github.m4gshm.storage.NotFoundException;
import io.github.m4gshm.utils.Jooq;
import io.r2dbc.postgresql.api.PostgresqlException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class PreparedTransactionServiceImpl implements PreparedTransactionService {

    private final Jooq jooq;

    @Getter
    private final Class<PreparedTransaction> entityClass = PreparedTransaction.class;

    private static PostgresqlException getPostgresqlException(Throwable e) {
        if (e instanceof PostgresqlException postgresqlException) {
            return postgresqlException;
        } else if (e != null) {
            var cause = e.getCause();
            return cause == null || e == cause ? null : getPostgresqlException(cause);
        } else {
            return null;
        }
    }

    private static Throwable ifNotExist(Object id, Throwable e) {
        var postgresqlException = getPostgresqlException(e);
        var notFound = postgresqlException != null && "42704".equals(postgresqlException.getErrorDetails().getCode());
        // var message = e.getMessage();
        // var notFound = message.endsWith(format("prepared transaction with identifier
        // \"%s\" does not exist", id));
        return notFound ? NotFoundException.newNotFoundException(e, PreparedTransaction.class, id) : e;
    }

    private static <T> Mono<T> notFoundable(Object id, Mono<T> mono) {
        return mono.onErrorMap(e -> ifNotExist(id, e));
    }

    @Override
    public Mono<Void> commit(String id) {
        return jooq.outOfTransaction(dsl -> notFoundable(id, TwoPhaseTransaction.commit(dsl, id)));
    }

    @Override
    public Mono<List<PreparedTransaction>> findAll() {
        return jooq.outOfTransaction(dsl -> TwoPhaseTransaction.listPrepared(dsl).collectList());
    }

    @Override
    public Mono<PreparedTransaction> findById(String id) {
        return jooq.outOfTransaction(dsl -> notFoundable(id, TwoPhaseTransaction.getPreparedById(dsl, id)));
    }

    @Override
    public Mono<Void> prepare(String id) {
        return jooq.inTransaction(dsl -> TwoPhaseTransaction.prepare(dsl, id));
    }

    @Override
    public Mono<Void> rollback(String id) {
        return jooq.outOfTransaction(dsl -> notFoundable(id, TwoPhaseTransaction.rollback(dsl, id)));
    }
}
