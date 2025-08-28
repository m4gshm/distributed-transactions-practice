package io.github.m4gshm.reactive.idempotent.consumer;

import static io.github.m4gshm.reactive.idempotent.consumer.MessageMaintenanceService.Partition.CURRENT;
import static io.github.m4gshm.reactive.idempotent.consumer.MessageMaintenanceService.Partition.NEXT;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.springframework.beans.factory.InitializingBean;

import io.github.m4gshm.reactive.idempotent.consumer.storage.tables.InputMessages;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class MessageStorageR2dbc implements InitializingBean, MessageStorage {
    MessageMaintenanceService maintenanceService;

    DslEnv dslEnv;
    InputMessages table;
    Clock clock;
    boolean createTable;
    boolean createCurrentPartition;
    boolean createNextPartition;

    @Override
    public void afterPropertiesSet() {
        var createTableRoutine = createTable ? maintenanceService.createTable(true) : Mono.<Void>empty();
        var now = LocalDate.now();
        if (createCurrentPartition) {
            createTableRoutine = createTableRoutine.then(maintenanceService.addPartition(now, CURRENT));
        }
        if (createNextPartition) {
            createTableRoutine = createTableRoutine.then(maintenanceService.addPartition(now, NEXT));
        }
        createTableRoutine.block();
    }

    @Override
    public Mono<Void> storeUnique(Message message) {
        return dslEnv.provide(dsl -> from(dsl.insertInto(table)
                .set(table.MESSAGE_ID, message.getMessageID())
                .set(table.SUBSCRIBER_ID, message.getSubscriberID())
                .set(table.CREATED_AT, OffsetDateTime.now(clock))
                .onDuplicateKeyIgnore()).flatMap(count -> {
                    if (count == 1) {
                        return Mono.empty();
                    } else {
                        return error(new MessageAlreadyProcessedException(message.getMessageID()));
                    }
                }));
    }

    @FunctionalInterface
    public interface DslEnv {
        Mono<Void> provide(Function<DSLContext, Mono<Void>> dslContextMonoFunction);
    }
}
