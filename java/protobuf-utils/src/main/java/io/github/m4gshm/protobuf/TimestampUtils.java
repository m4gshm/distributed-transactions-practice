package io.github.m4gshm.protobuf;

import com.google.protobuf.Timestamp;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.OffsetDateTime;

import static java.util.Optional.ofNullable;

@UtilityClass
public class TimestampUtils {
    public static Instant toInstant(Timestamp dateTime) {
        return Instant.ofEpochSecond(dateTime.getSeconds(), dateTime.getNanos());
    }

    public static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    public static Timestamp toTimestamp(OffsetDateTime offsetDateTime) {
        return ofNullable(offsetDateTime).map(OffsetDateTime::toInstant).map(TimestampUtils::toTimestamp).orElse(null);
    }
}
