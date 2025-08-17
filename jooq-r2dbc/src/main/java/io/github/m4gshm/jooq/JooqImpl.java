package io.github.m4gshm.jooq;

import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.r2dbc.connection.ConnectionHolder;
import org.springframework.transaction.reactive.TransactionContext;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.deferContextual;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class JooqImpl implements Jooq {
    TransactionalOperator operator;

    ConnectionFactory connectionFactory;

    Configuration configuration;

    public static DSLContext newDsl(ContextView context,
            ConnectionFactory connectionFactory,
            Configuration configuration) {
        var transactionContext = context.get(TransactionContext.class);
        var o = (ConnectionHolder) transactionContext.getResources().get(connectionFactory);
        var connection = o.getConnection();
        return DSL.using(connection, configuration.dialect(), configuration.settings());
    }

    @Override
    public <T> Mono<T> transactional(Function<DSLContext, Mono<T>> function) {
        return defer(() -> operator.execute(_ -> deferContextual(context -> {
            var dlsContextHolder = context.hasKey(DSLContextHolder.class) ? context.get(DSLContextHolder.class) : null;
            if (dlsContextHolder == null) {
                // log
                return function.apply(newDsl(context, connectionFactory, configuration));
            }
            for (;;) {
                var dslContext = dlsContextHolder.holder.get();
                if (dslContext != null) {
                    // log
                    return function.apply(dslContext);
                } else {
                    // log
                    dslContext = newDsl(context, connectionFactory, configuration);
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

    private static final class DSLContextHolder {
        private final AtomicReference<DSLContext> holder = new AtomicReference<>();
    }
}
