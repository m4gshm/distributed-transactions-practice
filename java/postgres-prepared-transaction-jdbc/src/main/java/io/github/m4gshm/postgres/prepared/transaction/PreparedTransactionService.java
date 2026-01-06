package io.github.m4gshm.postgres.prepared.transaction;

import io.github.m4gshm.storage.ReadOperations;

import java.util.List;

public interface PreparedTransactionService extends ReadOperations<PreparedTransaction, String> {
    void commit(String id);

    @Override
    List<PreparedTransaction> findAll();

    @Override
    PreparedTransaction findById(String id);

    void prepare(String id);

    void rollback(String id);
}
