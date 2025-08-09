package io.github.m4gshm.payments.data;

import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.ReadStorage;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import reactor.core.publisher.Mono;

public interface AccountStorage extends ReadStorage<Account, String> {
    Mono<LockResult> addLock(String clientId, @Positive double amount);

    Mono<Void> unlock(String clientId, @Positive double amount);

    Mono<BalanceResult> writeOff(String clientId, @Positive double amount);

    Mono<BalanceResult> topUp(String clientId, @Positive double replenishment);

    @Builder
    public record LockResult(boolean success, double insufficientAmount) {

    }

    @Builder
    public record BalanceResult(double balance) {

    }
}
