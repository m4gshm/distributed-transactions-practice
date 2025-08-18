package io.github.m4gshm.jooq;

import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.deferContextual;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.r2dbc.connection.ConnectionHolder;
import org.springframework.transaction.reactive.TransactionContext;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class JooqImpl implements Jooq {
    TransactionalOperator required;
    TransactionalOperator requiredNew;

    ConnectionFactory connectionFactory;

    Configuration configuration;

    private static Connection getConnection(ContextView context, ConnectionFactory connectionFactory) {
        var transactionContext = context.get(TransactionContext.class);
        var o = (ConnectionHolder) transactionContext.getResources().get(connectionFactory);
        return o.getConnection();
    }

    public static DSLContext newDsl(Configuration configuration, Connection connection
    ) {
        return DSL.using(connection, configuration.dialect(), configuration.settings());
    }

    private <T> Mono<T> execute(TransactionalOperator operator, Function<DSLContext, Mono<T>> function) {
        return defer(() -> operator.execute(_ -> deferContextual(context -> {
            var dlsContextHolder = context.hasKey(DSLContextHolder.class) ? context.get(DSLContextHolder.class) : null;
            var connection = getConnection(context, connectionFactory);
            if (dlsContextHolder == null) {
                // log
                return function.apply(newDsl(configuration, connection));
            }
            for (;;) {
                var dslContext = dlsContextHolder.holder.get();
                if (dslContext != null) {
                    // log
                    return function.apply(dslContext);
                } else {
                    // log
                    dslContext = newDsl(configuration, connection);
                    if (dlsContextHolder.holder.compareAndSet(null, dslContext)) {
                        return function.apply(dslContext);
                    }
                }
            }
        })).singleOrEmpty()).contextWrite(context -> {
            if (!context.hasKey(DSLContextHolder.class)) {
                return context.put(DSLContextHolder.class, new DSLContextHolder());
            }
            return context;
        });
    }

    @Override
    public <T> Mono<T> inTransaction(Function<DSLContext, Mono<T>> function) {
        return execute(required, function);
    }

    @Override
    public <T> Mono<T> newTransaction(Function<DSLContext, Mono<T>> function) {
        return execute(requiredNew, function);
    }

    private static final class DSLContextHolder {
        private final AtomicReference<DSLContext> holder = new AtomicReference<>();
    }
}
