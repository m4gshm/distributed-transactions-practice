package io.github.m4gshm.jooq;

import io.github.m4gshm.tracing.TraceService;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.reactivestreams.Publisher;
import org.springframework.transaction.TransactionExecution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.deferContextual;

@Slf4j
@FieldDefaults(makeFinal = true, level = PRIVATE)
public abstract class AbstractReactiveJooqImpl<TS extends TransactionExecution> implements ReactiveJooq {
    TransactionalExecutor<TS> required;
    TransactionalExecutor<TS> requiredNew;
    TransactionalExecutor<TS> neverTransaction;
    private final TransactionalExecutor<TS> supportTransaction;
    TraceService traceService;

    protected AbstractReactiveJooqImpl(TransactionalExecutor<TS> required,
            TransactionalExecutor<TS> requiredNew,
            TransactionalExecutor<TS> neverTransaction,
            TransactionalExecutor<TS> supportTransaction,
            TraceService traceService) {
        this.required = required;
        this.requiredNew = requiredNew;
        this.neverTransaction = neverTransaction;
        this.supportTransaction = supportTransaction;
        this.traceService = traceService;
    }

    protected <T> Mono<T> execute(String name,
                                  TransactionalExecutor<TS> operator,
                                  Function<DSLContext, Mono<T>> routine) {
        var trace = traceService.startNewObservation(name);
        return operator.execute(name, transaction -> deferContextual(context -> {
            try (var _ = traceService.startLocalEvent(trace, "execute:init")) {
                log.debug("jooq execute hasTransaction {}, isNewTransaction {}, isRollbackOnly {}",
                        transaction.hasTransaction(),
                        transaction.isNewTransaction(),
                        transaction.isRollbackOnly());
                return routine.apply(getDslContext(context)).doOnSubscribe(_ -> {
                    traceService.addEvent(trace, "execute:subscribe");
                }).doFinally(_ -> {
                    traceService.addEvent(trace, "execute:finally");
                });
            }
        }))
                .contextWrite(this::initDslContext)
                .contextWrite(context -> traceService.putToReactContext(context, trace))
                .singleOrEmpty()
                .doOnError(e -> {
                    log.error("transactional error", e);
                    traceService.addEvent(trace, "error");
                    traceService.error(trace, e);
                })
                .doOnSubscribe(_ -> {
                    log.trace("start {}", name);
                    traceService.addEvent(trace, "subscribe");
                })
                .doOnRequest(_ -> {
                    log.trace("request {}", name);
                    traceService.addEvent(trace, "request");
                })
                .doFinally(s -> {
                    log.trace("end {} with signal {}", name, s);
                    traceService.stop(trace);
                });
    }

    protected DSLContext getDslContext(ContextView context) {
        DSLContext actualDslContext = null;
        var dslContextHolder = context.get(DSLContextHolder.class);
        int i = 1;
        try {
            for (; actualDslContext == null; i++) {
                final var dslContext = dslContextHolder.holder.get();
                if (dslContext != null) {
                    log.trace("reuse dslContext");
                    actualDslContext = dslContext;
                } else {
                    var newDslContext = newDslContext(context);
                    if (dslContextHolder.holder.compareAndSet(null, newDslContext)) {
                        actualDslContext = newDslContext;
                    }
                }
            }
        } finally {
            if (i > 1) {
                log.debug("getDslContext attempts {}", i);
            }
        }
        return actualDslContext;
    }

    @Override
    public <T> Mono<T> inTransaction(String op, Function<DSLContext, Mono<T>> function) {
        return execute("inTransaction:" + op, required, function);
    }

    protected reactor.util.context.Context initDslContext(reactor.util.context.Context context) {
        if (!context.hasKey(DSLContextHolder.class)) {
            return context.put(DSLContextHolder.class, new DSLContextHolder());
        }
        return context;
    }

    protected abstract DSLContext newDslContext(ContextView context);

    @Override
    public <T> Mono<T> newTransaction(String op, Function<DSLContext, Mono<T>> function) {
        return execute("newTransaction:" + op, requiredNew, function);
    }

    @Override
    public <T> Mono<T> outOfTransaction(String op, Function<DSLContext, Mono<T>> function) {
        return execute("outOfTransaction:" + op, neverTransaction, function);
    }

    @Override
    public <T> Mono<T> supportTransaction(String op, Function<DSLContext, Mono<T>> function) {
        return execute("supportTransaction:" + op, supportTransaction, function);
    }

    private static final class DSLContextHolder {
        private final AtomicReference<DSLContext> holder = new AtomicReference<>();
    }

    public interface TransactionalExecutor<TS extends TransactionExecution> {
        <T> Flux<T> execute(String name, TransactionCallback<T, TS> action);

        interface TransactionCallback<T, TS extends TransactionExecution> extends Function<TS, Publisher<T>> {
            @Override
            Publisher<T> apply(TS status);
        }
    }
}
