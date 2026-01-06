package io.github.m4gshm.idempotent.consumer;

import lombok.NonNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static java.time.ZoneId.systemDefault;

public interface MessageStorageMaintenanceService {

    default void addPartition(@NonNull PartitionType partitionType, @NonNull OffsetDateTime moment) {
        addPartition(newPartitionDuration(getPartitionStart(partitionType, moment)));
    }

    void addPartition(PartitionDuration partition);

    void createTable();

    default PartitionDuration newPartitionDuration(LocalDate from) {
        return new PartitionDuration(from, from.plusDays(1));
    }

    default LocalDate getPartitionStart(@NonNull PartitionType partitionType, @NonNull LocalDate moment) {
        return switch (partitionType) {
            case NEXT -> moment.plusDays(1);
            case PREV -> moment.minusDays(1);
            case CURRENT -> moment;
        };
    }

    default LocalDate getPartitionStart(@NonNull PartitionType partitionType, @NonNull OffsetDateTime moment) {
        return getPartitionStart(partitionType, moment.toInstant().atZone(systemDefault()).toLocalDate());
    }

}
