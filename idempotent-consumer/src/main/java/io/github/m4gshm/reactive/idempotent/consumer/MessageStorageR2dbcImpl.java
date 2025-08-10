package io.github.m4gshm.reactive.idempotent.consumer;

import io.github.m4gshm.reactive.idempotent.consumer.storage.tables.InputMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.function.Function;

import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;

@Slf4j
@RequiredArgsConstructor
public class MessageStorageR2dbcImpl implements InitializingBean, MessageStorage {
    private final DslFactory dslFactory;
    private final InputMessages table;
    private final Clock clock;
    private final boolean createTable;

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshedEvent(ContextRefreshedEvent event) {
    }

    @Override
    public Mono<Void> storeUnique(Message message) {
        return dslFactory.apply(dsl -> from(dsl.insertInto(table)
                .set(table.MESSAGE_ID, message.getMessageID())
                .set(table.SUBSCRIBER_ID, message.getSubscriberID())
                .set(table.CREATED_AT, OffsetDateTime.now(clock))
                .onDuplicateKeyIgnore()
        ).flatMap(count -> {
            if (count == 1) {
                return Mono.empty();
            } else {
                return error(new MessageAlreadyProcessedException(message.getMessageID()));
            }
        }));
    }

    @Override
    public void afterPropertiesSet() {
        if (createTable) {
            dslFactory.apply(dsl -> {
                var primaryKey = table.getPrimaryKey();
                var ddl = dsl
                        .createTableIfNotExists(table)
                        .columns(table.fields());
                if (primaryKey != null) {
                    ddl = ddl.constraints(primaryKey.constraint());
                }
                return from(ddl).then();
            }).doOnError(t -> {
                log.error("check database state error", t);
            }).doOnSuccess(_ -> {
                log.debug("[{}] initialized successfully", table.getQualifiedName());
            }).block();
        }
    }

    @FunctionalInterface
    public interface DslFactory extends Function<Function<DSLContext, Mono<Void>>, Mono<Void>> {

    }
}
