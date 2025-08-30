package io.github.m4gshm.reactive.idempotent.consumer;

import static io.github.m4gshm.reactive.idempotent.consumer.MessageStorageMaintenanceService.Partition.CURRENT;
import static io.github.m4gshm.reactive.idempotent.consumer.MessageStorageMaintenanceService.Partition.NEXT;
import static io.github.m4gshm.reactive.idempotent.consumer.storage.tables.InputMessages.INPUT_MESSAGES;
import static java.time.format.DateTimeFormatter.ISO_DATE;
import static java.time.format.DateTimeFormatter.ofPattern;
import static org.springframework.scheduling.annotation.Scheduled.CRON_DISABLED;
import static reactor.core.publisher.Mono.from;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.Internal;
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
        var primaryKey = table.getPrimaryKey();

        var refs = new ArrayList<>(primaryKey.getFields());
        refs.add(INPUT_MESSAGES.CREATED_AT);

        return Internal.createUniqueKey(
                primaryKey.getTable(),
                primaryKey.getQualifiedName(),
                getArray(refs)
        );
    }

    @Override
    public Mono<Void> addPartition(@NonNull LocalDate moment, @NonNull Partition partition) {
        var partitionFrom = partition == NEXT
                ? moment.plusDays(1)
                : moment;
        var partitionTo = partitionFrom.plusDays(1);

        var partitionedTable = table.getName();
        var partitionSuffix = partitionFrom.format(ofPattern(partitionSuffixPattern));
        var partitionName = partitionedTable + "_" + partitionSuffix;
        return dslFactory.provide(dsl -> {
            return from(dsl.query("create table if not exists " + partitionName
                    + " partition of "
                    + partitionedTable
                    + " for values from ('"
                    + partitionFrom.format(ISO_DATE)
                    + "') TO ('"
                    + partitionTo.format(ISO_DATE)
                    + "')")).then();
        });
    }

    @Override
    public Mono<Void> createTable(boolean usePartition) {
        return dslFactory.provide(dsl -> {
            var primaryKey = usePartition
                    ? table.getPrimaryKey()
                    : getPrimaryKeyForPartitionedTable(table);
            var ddl = dsl
                    .createTableIfNotExists(table)
                    .columns(table.fields());
            if (primaryKey != null) {
                ddl = ddl.constraints(primaryKey.constraint());
            }
            return (usePartition
                    ? from(dsl.query(ddl.getSQL()
                            + " partition by range ("
                            + table.CREATED_AT.getName()
                            + ")"))
                    : from(ddl)).then();
        }).doOnError(t -> {
            log.error("createTable error: {}", table.getQualifiedName(), t);
        }).doOnSuccess(_ -> {
            log.trace("createTable success: {} {}", table.getQualifiedName(), usePartition ? "partitioned" : "");
        });
    }

    @Scheduled(cron = "${idempotent-consumer.create-partition-scheduler:" + CRON_DISABLED + "}")
    public void scheduledCreatePartition() {
        log.info("starting scheduled create partition");
        var now = LocalDate.now();
        addPartition(now, CURRENT).then(addPartition(now, NEXT)).block();
    }
}
