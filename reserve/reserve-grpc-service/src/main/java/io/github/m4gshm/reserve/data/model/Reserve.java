package io.github.m4gshm.reserve.data.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import org.springframework.validation.annotation.Validated;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Validated
@Valid
@Builder(toBuilder = true)
public record Reserve(String id,
                      String externalRef,
                      Status status,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt,
                      @Valid List<Item> items) {
    public enum Status {
        created,
        approved,
        released,
        cancelled;

        private static final Map<String, Status> byCode = Arrays.stream(Status.values()).collect(toMap(Status::getCode, status -> status));

        public static Status byCode(String code) {
            return byCode.get(code);
        }

        public String getCode() {
            return name();
        }
    }

    @Builder(toBuilder = true)
    public record Item(String id, @Positive Integer amount, Integer insufficient, boolean reserved) {
    }
}
