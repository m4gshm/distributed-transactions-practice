package io.github.m4gshm.postgres.prepared.transaction;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record PreparedTransaction(Integer transaction, String gid, OffsetDateTime prepared) {

}
