package io.github.m4gshm.reactive.idempotent.consumer;

import io.github.m4gshm.reactive.idempotent.consumer.storage.tables.InputMessages;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.InitializingBean;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.function.Function;

import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class MessageStorageR2dbc implements InitializingBean, MessageStorage {
    MessageMaintenanceService maintenanceService;
    DslEnv dslEnv;
    InputMessages table;
    Clock clock;
    boolean createTable;

    @Override
    public Mono<Void> storeUnique(Message message) {
        return dslEnv.provide(dsl -> from(dsl.insertInto(table)
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
            maintenanceService.createTable().block();
        }
    }

    @FunctionalInterface
    public interface DslEnv {
        Mono<Void> provide(Function<DSLContext, Mono<Void>> dslContextMonoFunction);
    }
}
