package io.github.m4gshm.idempotent.consumer;

import java.time.LocalDate;

public record PartitionDuration(LocalDate from, LocalDate to) {

}
