package io.github.m4gshm.idempotent.consumer;

import io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.TableField;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;
import java.util.List;

import static io.github.m4gshm.idempotent.consumer.MessageStorageMaintenanceJooqUtils.createPartition;
import static io.github.m4gshm.idempotent.consumer.PartitionType.CURRENT;
import static io.github.m4gshm.idempotent.consumer.PartitionType.NEXT;
import static org.springframework.scheduling.annotation.Scheduled.CRON_DISABLED;

@Slf4j
@RequiredArgsConstructor
public class MessageStorageMaintenanceServiceImpl implements MessageStorageMaintenanceService {
    private final DSLContext dsl;
    private final InputMessages table;
    private final String partitionSuffixPattern;

    @SuppressWarnings("unchecked")
    private static <R extends org.jooq.Record> TableField<R, ?>[] getArray(List<TableField<R, ?>> refs) {
        return (TableField<R, ?>[]) refs.toArray(new TableField[0]);
    }

    @Override
    public void addPartition(@NonNull PartitionDuration partition) {
        createPartition(dsl, table, partitionSuffixPattern, partition).execute();
    }

    @Override
    public void createTable() {
        var query = MessageStorageMaintenanceJooqUtils.createTable(dsl, table);
        log.debug("create table: {}", query);
        query.execute();
    }

    @Scheduled(cron = "${idempotent-consumer.create-partition-scheduler:" + CRON_DISABLED + "}")
    public void scheduledCreatePartition() {
        log.info("starting scheduled create partition");
        var now = OffsetDateTime.now();
        addPartition(CURRENT, now);
        addPartition(NEXT, now);
    }
}
