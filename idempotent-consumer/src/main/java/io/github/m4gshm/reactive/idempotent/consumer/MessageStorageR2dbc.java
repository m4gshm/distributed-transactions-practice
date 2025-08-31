package io.github.m4gshm.reactive.idempotent.consumer;

import static io.github.m4gshm.r2dbc.postgres.PostgresqlExceptionUtils.getPostgresqlException;
import static io.github.m4gshm.reactive.idempotent.consumer.MessageStorageMaintenanceService.PartitionType.CURRENT;
import static java.util.Optional.ofNullable;
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
import io.r2dbc.postgresql.api.PostgresqlException;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class MessageStorageR2dbc implements InitializingBean, MessageStorage {
    MessageStorageMaintenanceService maintenanceService;

    DslEnv dslEnv;
    InputMessages table;
    Clock clock;
    boolean createTable;
    boolean createPartition;
    boolean createPartitionOnStore;

    private static boolean isNoPartitionOfRelation(Throwable e) {
        return ofNullable(getPostgresqlException(e)).map(PostgresqlException::getErrorDetails)
                .filter(errorDetails -> {
                    var code = errorDetails.getCode();
                    var detailsMessage = errorDetails.getMessage();
                    return "23514".equals(code) && detailsMessage.startsWith("no partition of relation");
                })
                .isPresent();
    }

    @Override
    public void afterPropertiesSet() {
        var createTableRoutine = createTable ? maintenanceService.createTable() : Mono.<Void>empty();
        var now = OffsetDateTime.now(clock);
        if (createPartition) {
            createTableRoutine = createTableRoutine.then(maintenanceService.addPartition(CURRENT, now));
        }
        createTableRoutine.block();
    }

    private Mono<Integer> insert(Message message,
                                 OffsetDateTime createdAt,
                                 OffsetDateTime timestamp,
                                 LocalDate partitionId) {
        return dslEnv.provide(dsl -> from(dsl.insertInto(table)
                .set(table.ID, message.getMessageID())
                .set(table.SUBSCRIBER_ID, message.getSubscriberID())
                .set(table.CREATED_AT, createdAt)
                .set(table.EVENT_TIMESTAMP, timestamp)
                .set(table.PARTITION_ID, partitionId)
                .onDuplicateKeyIgnore()));
    }

    @Override
    public Mono<Void> storeUnique(Message message) {
        var timestamp = message.getTimestamp();
        var insert = insert(message,
                OffsetDateTime.now(clock),
                timestamp,
                maintenanceService.getPartitionStart(CURRENT, timestamp));

        var store = !createPartitionOnStore
                ? insert
                : insert.onErrorResume(e -> isNoPartitionOfRelation(e)
                        ? maintenanceService.addPartition(CURRENT, timestamp)
                                .then(insert)
                                .doOnSubscribe(s -> log.info("trying to insert after the current partition "
                                        + "has been created"))
                        : error(e));

        return store.flatMap(count -> {
            return (count == 1 ? Mono.empty() : error(new MessageAlreadyProcessedException(message.getMessageID())))
                    .then();
        }).then();
    }

    @FunctionalInterface
    public interface DslEnv {
        <T> Mono<T> provide(Function<DSLContext, Mono<T>> dslContextMonoFunction);
    }
}
