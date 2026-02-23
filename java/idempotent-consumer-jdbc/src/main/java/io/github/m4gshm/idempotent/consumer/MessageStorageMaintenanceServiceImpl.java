package io.github.m4gshm.idempotent.consumer;

import io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;

import static io.github.m4gshm.idempotent.consumer.MessageStorageMaintenanceJooqUtils.createPartition;
import static io.github.m4gshm.idempotent.consumer.PartitionType.CURRENT;
import static io.github.m4gshm.idempotent.consumer.PartitionType.NEXT;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.scheduling.annotation.Scheduled.CRON_DISABLED;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class MessageStorageMaintenanceServiceImpl implements MessageStorageMaintenanceService {
    DSLContext dsl;
    TransactionTemplate transactionTemplate;
    InputMessages table;
    String partitionSuffixPattern;
    Clock clock;
    boolean createTable;
    boolean createPartition;

    public void addPartition(@NonNull PartitionDuration partition) {
        createPartition(dsl, table, partitionSuffixPattern, partition).execute();
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void addPartition(@NonNull PartitionType partitionType, @NonNull OffsetDateTime moment) {
        transactionTemplate.executeWithoutResult(_ -> {
            addPartition(newPartitionDuration(getPartitionStart(partitionType, moment)));
        });
    }

    @PostConstruct
    public void afterPropertiesSet() {
        if (createTable) {
            createTable();
        }
        var now = OffsetDateTime.now(clock);
        if (createPartition) {
            addPartition(CURRENT, now);
        }
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void createTable() {
        transactionTemplate.executeWithoutResult(_ -> {
            var query = MessageStorageMaintenanceJooqUtils.createTable(dsl, table);
            log.debug("create table: {}", query);
            query.execute();
        });

    }

    @Scheduled(cron = "${idempotent-consumer.create-partition-scheduler:" + CRON_DISABLED + "}")
    public void scheduledCreatePartition() {
        log.info("starting scheduled create partition");
        transactionTemplate.executeWithoutResult(_ -> {
            var now = OffsetDateTime.now();
            addPartition(CURRENT, now);
            addPartition(NEXT, now);
        });
    }
}
