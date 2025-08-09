package io.github.m4gshm.reserve.data.r2dbc;

import lombok.Getter;

@Getter
public class ReleaseItemException extends RuntimeException {
    private final String id;
    private final int reserved;
    private final int totalAmount;

    public ReleaseItemException(String id, int reserved, int totalAmount) {
        super("release reserved item failed " + id + ", reserved: " + reserved + ", totalAmount: " + totalAmount);
        this.id = id;
        this.reserved = reserved;
        this.totalAmount = totalAmount;
    }
}
