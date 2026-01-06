package io.github.m4gshm.idempotent.consumer;

import io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages;
import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import org.jooq.RowCountQuery;

import static java.time.format.DateTimeFormatter.ISO_DATE;
import static java.time.format.DateTimeFormatter.ofPattern;

@UtilityClass
public class MessageStorageMaintenanceJooqUtils {
    public static RowCountQuery createPartition(DSLContext dsl,
                                                InputMessages table,
                                                String partitionSuffixPattern,
                                                PartitionDuration partition) {
        var partitionedTable = table.getName();
        var partitionSuffix = partition.from().format(ofPattern(partitionSuffixPattern));
        var partitionName = partitionedTable + "_" + partitionSuffix;
        return createPartition(dsl, partitionedTable, partitionName, partition);
    }

    public static RowCountQuery createPartition(DSLContext dsl,
                                                String partitionedTable,
                                                String partitionName,
                                                PartitionDuration partition) {
        return dsl.query("create table if not exists " + partitionName
                + " partition of "
                + partitionedTable
                + " for values from ('"
                + partition.from().format(ISO_DATE)
                + "') TO ('"
                + partition.to().format(ISO_DATE)
                + "')");
    }

    public static RowCountQuery createTable(DSLContext dsl, InputMessages table) {
        return dsl.query(dsl
                .createTableIfNotExists(table)
                .columns(table.fields())
                .primaryKey(table.getPrimaryKey().getFields())
                .getSQL()
                + " partition by range ("
                + table.PARTITION_ID.getName()
                + ")");
    }
}
