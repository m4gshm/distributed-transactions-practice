package io.github.m4gshm.payments.data;

import lombok.Getter;

@Getter
public class InvalidUnlockFundValueException extends RuntimeException {
    private final String clientId;

    public InvalidUnlockFundValueException(String clientId,
                                           double toUnlock, double locked) {
        super("invalid unlock funds value for client " + clientId + ", unlock " + toUnlock + ", actual" + locked);
        this.clientId = clientId;
    }
}
