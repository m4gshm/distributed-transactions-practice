package io.github.m4gshm.payments.data;

import io.github.m4gshm.storage.ReadStorage;
import io.github.m4gshm.payments.data.model.Account;
import lombok.Builder;
import reactor.core.publisher.Mono;

public interface AccountStorage extends ReadStorage<Account, String> {
    Mono<LockResult> addLock(Account account, double amount);

    @Builder
    public record LockResult(boolean success, double insufficientAmount) {

    }
}
