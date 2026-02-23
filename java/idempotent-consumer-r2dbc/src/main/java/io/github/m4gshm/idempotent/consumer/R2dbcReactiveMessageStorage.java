package io.github.m4gshm.idempotent.consumer;

import io.github.m4gshm.idempotent.consumer.storage.tables.records.InputMessagesRecord;
import io.r2dbc.postgresql.api.PostgresqlException;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.InsertReturningStep;
import org.springframework.beans.factory.InitializingBean;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.function.Function;

import static io.github.m4gshm.idempotent.consumer.PartitionType.CURRENT;
import static io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages.INPUT_MESSAGES;
import static io.github.m4gshm.r2dbc.postgres.PostgresqlExceptionUtils.getPostgresqlException;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class R2dbcReactiveMessageStorage implements InitializingBean, ReactiveMessageStorage {
    ReactiveMessageStorageMaintenanceService maintenanceService;

    DslContextProvider dslContextProvider;
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

    private InsertReturningStep<InputMessagesRecord> getInputMessagesRecordInsertReturningStep(Message message,
                                                                                               OffsetDateTime createdAt,
                                                                                               OffsetDateTime timestamp,
                                                                                               LocalDate partitionId,
                                                                                               DSLContext dsl) {
        return dsl.insertInto(INPUT_MESSAGES)
                .set(INPUT_MESSAGES.ID, message.getMessageID())
                .set(INPUT_MESSAGES.SUBSCRIBER_ID, message.getSubscriberID())
                .set(INPUT_MESSAGES.CREATED_AT, createdAt)
                .set(INPUT_MESSAGES.EVENT_TIMESTAMP, timestamp)
                .set(INPUT_MESSAGES.PARTITION_ID, partitionId)
                .onDuplicateKeyIgnore();
    }

    private Mono<Integer> insert(Message message,
                                 OffsetDateTime createdAt,
                                 OffsetDateTime timestamp,
                                 LocalDate partitionId) {
        return dslContextProvider.provide("insert",
                dsl -> from(getInputMessagesRecordInsertReturningStep(message,
                        createdAt,
                        timestamp,
                        partitionId,
                        dsl)));
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
    public interface DslContextProvider {
        <T> Mono<T> provide(String op, Function<DSLContext, Mono<T>> dslContextMonoFunction);
    }
}
