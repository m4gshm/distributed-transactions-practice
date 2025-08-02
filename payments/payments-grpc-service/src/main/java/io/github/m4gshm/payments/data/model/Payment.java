package io.github.m4gshm.payments.data.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Valid
@Builder(toBuilder = true)
public record Payment(String id,
                      String externalRef,
                      String clientId,
                      Status status,
                      @Positive Double amount,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt) {

    public enum Status {
        created,
        approved,
        insufficient,
        paid,
        cancelled;

        private final static Map<String, Status> byCode = Arrays.stream(Status.values()).collect(toMap(Status::getCode, status -> status));

        public static Status byCode(String code) {
            return byCode.get(code);
        }

        public String getCode() {
            return name();
        }
    }

}
