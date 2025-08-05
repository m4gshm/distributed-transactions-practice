package io.github.m4gshm.payments.data;

import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.ReadStorage;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import reactor.core.publisher.Mono;

public interface AccountStorage extends ReadStorage<Account, String> {
    Mono<LockResult> addLock(Account account, double amount);
    Mono<Void> unlock(Account account, double amount);

    Mono<WriteOffResult> writeOff(Account account, @Positive Double amount);

    @Builder
    public record LockResult(boolean success, double insufficientAmount) {

    }

    @Builder
    public record WriteOffResult(double balance) {

    }
}
