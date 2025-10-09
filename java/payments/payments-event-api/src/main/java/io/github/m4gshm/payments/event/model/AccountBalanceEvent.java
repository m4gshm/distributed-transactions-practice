package io.github.m4gshm.payments.event.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.OffsetDateTime;

import static com.fasterxml.jackson.annotation.JsonFormat.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS;
import static com.fasterxml.jackson.annotation.JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;
import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;

@Builder
public record AccountBalanceEvent(String requestId, String clientId, double balance,
                                  @JsonFormat(shape = STRING,
                                          without = {
                                                  WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS,
                                                  READ_DATE_TIMESTAMPS_AS_NANOSECONDS,
                                          }) OffsetDateTime timestamp){
}
