package io.github.m4gshm.payments.data.r2dbc;

import lombok.Getter;

@Getter
public class InvalidUnlockFundValueException extends RuntimeException {
    private final String clientId;

    public InvalidUnlockFundValueException(String clientId,
                                           double value) {
        super("invalid unlock funds value " + value + " for client " + clientId);
        this.clientId = clientId;
    }
}
