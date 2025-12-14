package io.github.m4gshm;

import lombok.experimental.UtilityClass;

import java.time.OffsetDateTime;

import static java.util.Optional.ofNullable;

@UtilityClass
public class DateTimeUtils {
    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }
}
