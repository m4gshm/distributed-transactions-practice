package io.github.m4gshm.payments.data.model;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import payments.data.access.jooq.enums.PaymentStatus;

@Valid
@Builder(toBuilder = true)
public record Payment(
                      String id,
                      String externalRef,
                      String clientId,
                      PaymentStatus status,
                      @Positive Double amount,
                      @Positive Double insufficient,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt) {

//    public enum Status implements EnumWithCode<Status> {
//            CREATED,
//            HOLD,
//            INSUFFICIENT,
//            PAID,
//            CANCELLED;
//
//        public static Status byCode(String code) {
//            return EnumWithCodeUtils.getByCode(Status.class, code);
//        }
//    }

}
