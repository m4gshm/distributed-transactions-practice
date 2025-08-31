package io.github.m4gshm.payments.data;

import java.time.OffsetDateTime;

import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.ReadOperations;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import reactor.core.publisher.Mono;

public interface AccountStorage extends ReadOperations<Account, String> {
    Mono<LockResult> addLock(String clientId, @Positive double amount);

    Mono<Void> unlock(String clientId, @Positive double amount);

    Mono<BalanceResult> writeOff(String clientId, @Positive double amount);

    Mono<BalanceResult> addAmount(String clientId, @Positive double replenishment);

    @Builder
    record LockResult(boolean success, double insufficientAmount) {

    }

    @Builder
    record BalanceResult(double balance, OffsetDateTime timestamp) {

    }
}
