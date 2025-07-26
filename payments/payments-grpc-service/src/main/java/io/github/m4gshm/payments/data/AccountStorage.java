package io.github.m4gshm.payments.data;

import io.github.m4gshm.storage.ReadStorage;
import io.github.m4gshm.payments.data.model.Account;
import reactor.core.publisher.Mono;

public interface AccountStorage extends ReadStorage<Account, String> {
    Mono<Boolean> addLock(Account account, Double amount, String txid, boolean twoPhaseCommit);
}
