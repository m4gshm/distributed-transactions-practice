package io.github.m4gshm.idempotent.consumer;

public interface MessageStorage {
    void storeUnique(Message message);
}
