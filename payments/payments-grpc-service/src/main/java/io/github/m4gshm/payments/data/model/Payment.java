package io.github.m4gshm.payments.data.model;

import io.github.m4gshm.EnumWithCode;
import io.github.m4gshm.EnumWithCodeUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.time.OffsetDateTime;

@Valid
@Builder(toBuilder = true)
public record Payment(
                      String id,
                      String externalRef,
                      String clientId,
                      Status status,
                      @Positive Double amount,
                      @Positive Double insufficient,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt) {

    public enum Status implements EnumWithCode<Status> {
            created,
            hold,
            insufficient,
            paid,
            cancelled;

        public static Status byCode(String code) {
            return EnumWithCodeUtils.getByCode(Status.class, code);
        }
    }

}
