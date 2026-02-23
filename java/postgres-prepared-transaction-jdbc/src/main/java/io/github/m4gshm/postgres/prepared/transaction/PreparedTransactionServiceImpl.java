package io.github.m4gshm.postgres.prepared.transaction;

import io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransactionUtils.PrepareTransactionException;
import io.github.m4gshm.storage.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;

import java.util.List;
import java.util.function.Supplier;

import static io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransactionUtils.getPreparedById;
import static io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransactionUtils.listPrepared;
import static io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransactionUtils.newPreparedTransaction;
import static io.github.m4gshm.r2dbc.postgres.PostgresqlExceptionUtils.getPostgresqlException;
import static io.github.m4gshm.storage.NotFoundException.newNotFoundException;

@Slf4j
@RequiredArgsConstructor
public class PreparedTransactionServiceImpl implements PreparedTransactionService {

    private final DSLContext dsl;

    @Getter
    private final Class<PreparedTransaction> entityClass = PreparedTransaction.class;

    private static NotFoundException ifNotExist(Object id, Exception e) {
        var postgresqlException = getPostgresqlException(e);
        var notFound = postgresqlException != null && "42704".equals(postgresqlException.getSQLState());
        return notFound ? newNotFoundException(e, PreparedTransaction.class, id) : null;
    }

    private static <T> T notFoundable(Object id, Supplier<T> routine) {
        try {
            return routine.get();
        } catch (Exception e) {
            var notFoundException = ifNotExist(id, e);
            if (notFoundException != null) {
                throw notFoundException;
            }
            throw e;
        }
    }

    @Override
    public void commit(String id) {
        notFoundable(id, () -> TwoPhaseTransactionUtils.commit(dsl, id));
    }

    @Override
    public List<PreparedTransaction> findAll() {
        return listPrepared(dsl)
                .stream()
                .map(TwoPhaseTransactionUtils::newPreparedTransaction)
                .toList();
    }

    @Override
    public PreparedTransaction findById(String id) {
        return notFoundable(id, () -> {
            var record = getPreparedById(dsl, id).fetchOne();
            return record != null ? newPreparedTransaction(record) : null;
        });
    }

    @Override
    public void prepare(String id) {
        try {
            TwoPhaseTransactionUtils.prepare(dsl, id).execute();
        } catch (Exception e) {
            throw new PrepareTransactionException(id, e);
        }
    }

    @Override
    public void rollback(String id) {
        notFoundable(id, TwoPhaseTransactionUtils.rollback(dsl, id)::execute);
    }
}
