package io.github.m4gshm.jooq;

import io.github.m4gshm.tracing.TraceService;
import io.opentelemetry.api.OpenTelemetry;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.r2dbc.connection.ConnectionHolder;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionContext;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import static lombok.AccessLevel.PRIVATE;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_NEVER;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.transaction.reactive.TransactionalOperator.create;

@Slf4j
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class R2dbcReactiveJooqImpl extends AbstractReactiveJooqImpl<ReactiveTransaction> {
    ConnectionFactory connectionFactory;
    Configuration configuration;

    static DSLContext newDsl(Configuration configuration) {
        return DSL.using(configuration);
    }

    static DSLContext newDsl(Configuration configuration, Connection connection) {
        var connectionFactory = DSL.using(connection, configuration.dialect(), configuration.settings())
                .configuration()
                .connectionFactory();
        var configuration1 = configuration.derive(connectionFactory);
        return DSL.using(configuration1);
    }

    static TransactionalExecutor<ReactiveTransaction> newTransactionalExecutor(
                                                                               ReactiveTransactionManager transactionManager,
                                                                               int propagationBehavior
    ) {
        var operator = create(transactionManager, new DefaultTransactionDefinition(propagationBehavior));
        return new TransactionalExecutor<>() {
            @Override
            public <T> Flux<T> execute(String name, TransactionCallback<T, ReactiveTransaction> action) {
                return operator.execute(action::apply);
            }
        };
    }

    public R2dbcReactiveJooqImpl(ReactiveTransactionManager transactionManager,
            ConnectionFactory connectionFactory,
            Configuration configuration,
            OpenTelemetry openTelemetry,
            TraceService traceService) {
        super(
                newTransactionalExecutor(transactionManager, PROPAGATION_REQUIRED),
                newTransactionalExecutor(transactionManager, PROPAGATION_REQUIRES_NEW),
                newTransactionalExecutor(transactionManager, PROPAGATION_NEVER),
                openTelemetry,
                traceService
        );
        this.connectionFactory = connectionFactory;
        this.configuration = configuration;
    }

    private Connection getConnection(ContextView context) {
        var transactionContext = context.get(TransactionContext.class);
        var connectionHolder = (ConnectionHolder) transactionContext.getResources().get(connectionFactory);
        return connectionHolder != null ? connectionHolder.getConnection() : null;
    }

    @Override
    protected DSLContext getDslContext(ContextView context) {
//        return this.dslContext;
        return super.getDslContext(context);
    }

    @Override
    protected DSLContext newDslContext(ContextView context) {
        var connection = getConnection(context);
        if (connection != null) {
            log.trace("reuse connection for new dslContext {}", connection);
            return newDsl(configuration, connection);
        } else {
            return newDsl(configuration);
        }
    }
}
