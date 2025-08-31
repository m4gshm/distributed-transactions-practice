package io.github.m4gshm.reactive.idempotent.consumer;

import static io.github.m4gshm.reactive.idempotent.consumer.MessageStorageMaintenanceService.PartitionType.CURRENT;
import static io.github.m4gshm.reactive.idempotent.consumer.MessageStorageMaintenanceService.PartitionType.NEXT;
import static java.time.format.DateTimeFormatter.ISO_DATE;
import static java.time.format.DateTimeFormatter.ofPattern;
import static org.springframework.scheduling.annotation.Scheduled.CRON_DISABLED;
import static reactor.core.publisher.Mono.from;

import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.springframework.scheduling.annotation.Scheduled;

import io.github.m4gshm.reactive.idempotent.consumer.storage.tables.InputMessages;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class MessageStorageMaintenanceR2Dbc implements MessageStorageMaintenanceService {
    private final MessageStorageR2dbc.DslEnv dslFactory;
    private final InputMessages table;
    private final String partitionSuffixPattern;

    @SuppressWarnings("unchecked")
    private static <R extends org.jooq.Record> TableField<R, ?>[] getArray(List<TableField<R, ?>> refs) {
        return (TableField<R, ?>[]) refs.toArray(new TableField[0]);
    }

    public static UniqueKey<?> getPrimaryKeyForPartitionedTable(InputMessages table) {
        return table.getPrimaryKey();
    }

    @Override
    public Mono<Void> addPartition(@NonNull PartitionDuration partition) {
        var partitionedTable = table.getName();
        var partitionSuffix = partition.from().format(ofPattern(partitionSuffixPattern));
        var partitionName = partitionedTable + "_" + partitionSuffix;
        return dslFactory.provide(dsl -> from(dsl.query("create table if not exists " + partitionName
                + " partition of "
                + partitionedTable
                + " for values from ('"
                + partition.from().format(ISO_DATE)
                + "') TO ('"
                + partition.to().format(ISO_DATE)
                + "')")).then());
    }

    @Override
    public Mono<Void> createTable() {
        return dslFactory.provide(dsl -> {
            var query = dsl.query(dsl
                    .createTableIfNotExists(table)
                    .columns(table.fields())
                    .primaryKey(table.getPrimaryKey().getFields())
                    .getSQL()
                    + " partition by range ("
                    + table.PARTITION_ID.getName()
                    + ")");
            log.debug("create table: {}", query);
            return from(query).then();
        }).doOnError(t -> {
            log.error("createTable error: {}", table.getQualifiedName(), t);
        }).doOnSuccess(_ -> {
            log.trace("createTable success {}", table.getQualifiedName());
        });
    }

    @Scheduled(cron = "${idempotent-consumer.create-partition-scheduler:" + CRON_DISABLED + "}")
    public void scheduledCreatePartition() {
        log.info("starting scheduled create partition");
        var now = OffsetDateTime.now();
        addPartition(CURRENT, now).then(addPartition(NEXT, now)).block();
    }
}
