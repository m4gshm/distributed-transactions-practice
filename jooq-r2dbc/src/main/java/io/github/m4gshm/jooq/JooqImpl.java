package io.github.m4gshm.jooq;

import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.r2dbc.connection.ConnectionHolder;
import org.springframework.transaction.reactive.TransactionContext;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.function.Function;

import static reactor.core.publisher.Mono.deferContextual;

@RequiredArgsConstructor
public class JooqImpl implements Jooq {
    private final TransactionalOperator operator;
    private final ConnectionFactory connectionFactory;

    public static DSLContext getDsl(ContextView context, ConnectionFactory connectionFactory) {
        var transactionContext = context.get(TransactionContext.class);
        var o = (ConnectionHolder) transactionContext.getResources().get(connectionFactory);
        return DSL.using(o.getConnection());
    }

    @Override
    public <T> Mono<T> transactional(Function<DSLContext, Mono<T>> function) {
        return operator.execute(status -> {
            return deferContextual(context -> {
                return function.apply(getDsl(context, connectionFactory));
            });
        }).singleOrEmpty();
    }
}
