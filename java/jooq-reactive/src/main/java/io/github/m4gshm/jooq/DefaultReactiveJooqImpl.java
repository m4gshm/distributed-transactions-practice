package io.github.m4gshm.jooq;

import io.github.m4gshm.tracing.TraceService;
import io.opentelemetry.api.OpenTelemetry;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import org.jooq.DSLContext;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collection;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_NEVER;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.support.TransactionSynchronizationManager.getResource;
import static org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource;

@FieldDefaults(level = PRIVATE, makeFinal = true)
public class DefaultReactiveJooqImpl extends AbstractReactiveJooqImpl<TransactionExecution> {
    DSLContext dslContext;

    private static TransactionalExecutor<TransactionExecution> newTransactionalExecutor(
                                                                                        JdbcTransactionManager transactionManager,
                                                                                        int propagationBehavior) {
        return new TransactionalExecutor<>() {

            private static void bindSynchronization(Collection<TransactionSynchronization> synchronizations) {
                if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.initSynchronization();
                }
                if (TransactionSynchronizationManager.getSynchronizations() != synchronizations) {
                    // copy ???
                    for (var synchronization : synchronizations) {
                        TransactionSynchronizationManager.registerSynchronization(synchronization);
                    }
                }
            }

            private static ConnectionHolder getConnectionHolder(DataSource dataSource) {
                return (ConnectionHolder) getResource(dataSource);
            }

            @SneakyThrows
            private static boolean isClosed(Connection connection) {
                return connection != null && connection.isClosed();
            }

            private void bindIfNeed(DataSource dataSource, DefaultTransactionStatus transaction) {
                var currentConnectionHolder = getConnectionHolder(dataSource);
                var txConnectionHolder = ((JdbcTransactionObjectSupport) transaction.getTransaction())
                        .getConnectionHolder();
                if (currentConnectionHolder == null) {
                    // log
                    TransactionSynchronizationManager.bindResource(dataSource, txConnectionHolder);
                } else if (currentConnectionHolder != txConnectionHolder) {
                    throw new IllegalStateException("mismatching  of connectionHolders: current "
                            + currentConnectionHolder
                            + ", but expected "
                            + txConnectionHolder
                            + " or null");
                }
            }

            @Override
            public <T> Flux<T> execute(String name, TransactionCallback<T, TransactionExecution> action) {
                var definition = new DefaultTransactionDefinition(TransactionDefinition.withDefaults());
                definition.setPropagationBehavior(propagationBehavior);
                var dataSource = getDataSource();
                var currentConnectionHolder = getConnectionHolder(dataSource);
                var connection = currentConnectionHolder != null ? currentConnectionHolder.getConnection() : null;
                if (isClosed(connection)) {
                    TransactionSynchronizationManager.clear();
                    unbindResource(dataSource);
                    currentConnectionHolder.released();
                    currentConnectionHolder.clear();
                }
                var status = (DefaultTransactionStatus) transactionManager.getTransaction(definition);
                var synchronizations = TransactionSynchronizationManager.getSynchronizations();
                var newTransaction1 = status.isNewTransaction();
                return Flux.from(action.apply(status)).doOnSubscribe(s -> {
                    bindSynchronization(synchronizations);
                    bindIfNeed(dataSource, status);
                }).doFinally(s -> {
                    bindSynchronization(synchronizations);
                    bindIfNeed(dataSource, status);

                    var newTransaction = status.isNewTransaction();
                    var nt = newTransaction1;

                    if (s == SignalType.ON_ERROR || s == SignalType.CANCEL) {
                        if (newTransaction) {
                            transactionManager.rollback(status);
                        } else {
                            status.setRollbackOnly();
                            unbindResource(dataSource);
                        }
                    } else {
                        if (newTransaction) {
                            transactionManager.commit(status);
                        } else {
                            unbindResource(dataSource);
                        }
                    }

                    var newSynchronization = status.isNewSynchronization();
                    if (newSynchronization) {
                        TransactionSynchronizationManager.clear();
                    }
                });
            }

            private DataSource getDataSource() {
                return requireNonNull(transactionManager.getDataSource(), "transactionManager.getDataSource()");
            }

        };
    }

    public DefaultReactiveJooqImpl(DSLContext dslContext,
            JdbcTransactionManager transactionManager,
            OpenTelemetry openTelemetry,
            TraceService traceService) {
        super(
                newTransactionalExecutor(transactionManager, PROPAGATION_REQUIRED),
                newTransactionalExecutor(transactionManager, PROPAGATION_REQUIRES_NEW),
                newTransactionalExecutor(transactionManager, PROPAGATION_NEVER),
                openTelemetry,
                traceService
        );
        this.dslContext = dslContext;
    }

    @Override
    protected DSLContext getDslContext(ContextView context) {
        return this.dslContext;
    }

    @Override
    protected Context initDslContext(Context context) {
        return context;
    }

    @Override
    protected DSLContext newDslContext(ContextView context) {
        return dslContext;
    }
}
