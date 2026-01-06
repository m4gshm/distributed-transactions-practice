package io.github.m4gshm.idempotent.consumer;

import static java.time.ZoneId.systemDefault;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.NonNull;
import reactor.core.publisher.Mono;

public interface ReactiveMessageStorageMaintenanceService {

    default Mono<Void> addPartition(@NonNull PartitionType partitionType, @NonNull OffsetDateTime moment) {
        return addPartition(newPartitionDuration(getPartitionStart(partitionType, moment)));
    }

    Mono<Void> addPartition(PartitionDuration partition);

    Mono<Void> createTable();

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
