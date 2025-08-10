package io.github.m4gshm.reserve.data.r2dbc;

import lombok.Getter;

@Getter
public class InvalidReserveValueException extends RuntimeException {
    private final String id;
    private final int value;

    public InvalidReserveValueException(String id, int value) {
        super("invalid reserve value " + value + " for item " + id);
        this.id = id;
        this.value = value;
    }
}
