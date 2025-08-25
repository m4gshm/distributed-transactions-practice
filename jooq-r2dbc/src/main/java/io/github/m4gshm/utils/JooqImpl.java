package io.github.m4gshm.utils;

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
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class JooqImpl implements Jooq {
    TransactionalOperator required;
    TransactionalOperator requiredNew;
    TransactionalOperator neverTransaction;

    ConnectionFactory connectionFactory;

    Configuration configuration;

    public static DSLContext newDsl(Configuration configuration, Connection connection) {
        log.debug("create DSLContext with Connection {}", connection);
        return DSL.using(connection, configuration.dialect(), configuration.settings());
    }

    private static DSLContext newDsl(Configuration configuration, ConnectionFactory connectionFactory) {
        log.debug("create DSLContext with ConnectionFactory {}", connectionFactory);
        return DSL.using(connectionFactory, configuration.dialect(), configuration.settings());
    }

    private <T> Mono<T> execute(TransactionalOperator operator, Function<DSLContext, Mono<T>> function) {
        return defer(() -> operator.execute(transaction -> deferContextual(context -> {
            var dslContextHolder = context.get(DSLContextHolder.class);
            var connection = getConnection(context);
            for (;;) {
                final var dslContext = dslContextHolder.holder.get();
                if (dslContext != null) {
                    // log
                    return function.apply(dslContext);
                } else {
                    // log
                    var newDslContext = connection != null
                            ? newDsl(configuration, connection)
                            : newDsl(configuration, connectionFactory);
                    if (dslContextHolder.holder.compareAndSet(null, newDslContext)) {
                        return function.apply(newDslContext);
                    }
                }
            }
        })).singleOrEmpty()).contextWrite(context -> {
            if (!context.hasKey(DSLContextHolder.class)) {
                return context.put(DSLContextHolder.class, new DSLContextHolder());
            }
            return context;
        }).doOnError(e -> {
            log.error("transactional error", e);
        });
    }

    private Connection getConnection(ContextView context) {
        var transactionContext = context.get(TransactionContext.class);
        var o = (ConnectionHolder) transactionContext.getResources().get(connectionFactory);
        return o != null ? o.getConnection() : null;
    }

    @Override
    public <T> Mono<T> inTransaction(Function<DSLContext, Mono<T>> function) {
        return execute(required, function);
    }

    @Override
    public <T> Mono<T> newTransaction(Function<DSLContext, Mono<T>> function) {
        return execute(requiredNew, function);
    }

    @Override
    public <T> Mono<T> outOfTransaction(Function<DSLContext, Mono<T>> function) {
        return execute(neverTransaction, function);
    }

    private static final class DSLContextHolder {
        private final AtomicReference<DSLContext> holder = new AtomicReference<>();
    }
}
