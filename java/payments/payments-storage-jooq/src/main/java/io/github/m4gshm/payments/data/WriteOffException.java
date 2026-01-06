package io.github.m4gshm.payments.data;

import lombok.Getter;

@Getter
public class WriteOffException extends RuntimeException {

    private final double insufficientAmount;
    private final double insufficientHold;

    public WriteOffException(double insufficientAmount,
            double insufficientHold) {
        super("write-off failed"
                + (insufficientAmount > 0 ? ", insufficientAmount: " + insufficientAmount : "")
                + (insufficientHold > 0 ? ", insufficientHold: " + insufficientHold : ""));
        this.insufficientAmount = insufficientAmount;
        this.insufficientHold = insufficientHold;
    }
}
