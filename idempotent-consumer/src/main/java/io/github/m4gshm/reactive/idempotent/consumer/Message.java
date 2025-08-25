package io.github.m4gshm.reactive.idempotent.consumer;

public interface Message {
    String getMessageID();

    String getSubscriberID();
}
