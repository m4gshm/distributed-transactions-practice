package io.github.m4gshm.idempotent.consumer;

import io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.TableField;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

import static io.github.m4gshm.idempotent.consumer.MessageStorageMaintenanceJooqUtils.createPartition;
import static io.github.m4gshm.idempotent.consumer.PartitionType.CURRENT;
import static io.github.m4gshm.idempotent.consumer.PartitionType.NEXT;
import static org.springframework.scheduling.annotation.Scheduled.CRON_DISABLED;
import static reactor.core.publisher.Mono.from;

@Slf4j
@RequiredArgsConstructor
public class R2dbcReactiveMessageStorageMaintenanceService implements ReactiveMessageStorageMaintenanceService {
    private final R2dbcReactiveMessageStorage.DslContextProvider dslFactory;
    private final InputMessages table;
    private final String partitionSuffixPattern;

    @SuppressWarnings("unchecked")
    private static <R extends org.jooq.Record> TableField<R, ?>[] getArray(List<TableField<R, ?>> refs) {
        return (TableField<R, ?>[]) refs.toArray(new TableField[0]);
    }

    @Override
    public Mono<Void> addPartition(@NonNull PartitionDuration partition) {
        return dslFactory.provide("addPartition", dsl -> {
            return from(createPartition(dsl, table, partitionSuffixPattern, partition)).then();
        }).name("addPartition");
    }

    @Override
    public Mono<Void> createTable() {
        return dslFactory.provide("createTable", dsl -> {
            var query = MessageStorageMaintenanceJooqUtils.createTable(dsl, table);
            log.debug("create table: {}", query);
            return from(query).then();
        }).doOnError(t -> {
            log.error("createTable error: {}", table.getQualifiedName(), t);
        }).doOnSuccess(_ -> {
            log.trace("createTable success {}", table.getQualifiedName());
        }).name("createTable");
    }

    @Scheduled(cron = "${idempotent-consumer.create-partition-scheduler:" + CRON_DISABLED + "}")
    public void scheduledCreatePartition() {
        log.info("starting scheduled create partition");
        var now = OffsetDateTime.now();
        addPartition(CURRENT, now).then(addPartition(NEXT, now)).block();
    }
}
