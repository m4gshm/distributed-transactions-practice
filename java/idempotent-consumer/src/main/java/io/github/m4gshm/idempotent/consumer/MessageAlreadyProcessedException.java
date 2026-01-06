package io.github.m4gshm.idempotent.consumer;

import lombok.Getter;

@Getter
public class MessageAlreadyProcessedException extends RuntimeException {
    private final Object id;

    public MessageAlreadyProcessedException(Object id) {
        super("Message with id " + id + " already processed");
        this.id = id;
    }
}
