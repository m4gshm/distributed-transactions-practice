package io.github.m4gshm.payments.data;

import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.ReadOperations;
import jakarta.validation.constraints.Positive;

public interface AccountStorage extends ReadOperations<Account, String> {
    BalanceResult addAmount(String clientId, @Positive double replenishment);

    LockResult addLock(String clientId, @Positive double amount);

    void unlock(String clientId, @Positive double amount) throws InvalidUnlockFundValueException;

    BalanceResult writeOff(String clientId, @Positive double amount) throws WriteOffException;
}
