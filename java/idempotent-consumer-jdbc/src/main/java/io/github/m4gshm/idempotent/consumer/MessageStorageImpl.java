package io.github.m4gshm.idempotent.consumer;

import io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.InitializingBean;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.github.m4gshm.idempotent.consumer.PartitionType.CURRENT;
import static io.github.m4gshm.r2dbc.postgres.PostgresqlExceptionUtils.getPostgresqlException;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class MessageStorageImpl implements InitializingBean, MessageStorage {
    MessageStorageMaintenanceService maintenanceService;

    DSLContext dsl;
    InputMessages table;
    Clock clock;
    boolean createTable;
    boolean createPartition;
    boolean createPartitionOnStore;

    private static boolean isNoPartitionOfRelation(Throwable e) {
        return ofNullable(getPostgresqlException(e)).filter(errorDetails -> {
            var code = errorDetails.getSQLState();
            var detailsMessage = errorDetails.getMessage();
            return "23514".equals(code) && detailsMessage.startsWith("no partition of relation");
        }).isPresent();
    }

    @Override
    public void afterPropertiesSet() {
        if (createTable) {
            maintenanceService.createTable();
        }
        var now = OffsetDateTime.now(clock);
        if (createPartition) {
            maintenanceService.addPartition(CURRENT, now);
        }
    }

    private int insert(Message message,
                       OffsetDateTime createdAt,
                       OffsetDateTime timestamp,
                       LocalDate partitionId) {
        return dsl.insertInto(table)
                .set(table.ID, message.getMessageID())
                .set(table.SUBSCRIBER_ID, message.getSubscriberID())
                .set(table.CREATED_AT, createdAt)
                .set(table.EVENT_TIMESTAMP, timestamp)
                .set(table.PARTITION_ID, partitionId)
                .onDuplicateKeyIgnore()
                .execute();
    }

    @Override
    public void storeUnique(Message message) {
        var timestamp = message.getTimestamp();
        Supplier<Integer> insert = () -> insert(message,
                OffsetDateTime.now(clock),
                timestamp,
                maintenanceService.getPartitionStart(CURRENT, timestamp));
        int count;
        try {
            count = insert.get();
        } catch (Exception e) {
            if (createPartitionOnStore && isNoPartitionOfRelation(e)) {
                log.info("trying to insert after the current partition "
                        + "has been created");
                maintenanceService.addPartition(CURRENT, timestamp);
                count = insert.get();
            } else {
                throw e;
            }
        }
        if (count != 1) {
            throw new MessageAlreadyProcessedException(message.getMessageID());
        }
    }
}
