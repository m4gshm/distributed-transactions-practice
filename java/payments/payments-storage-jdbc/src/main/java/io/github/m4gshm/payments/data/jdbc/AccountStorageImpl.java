package io.github.m4gshm.payments.data.jdbc;

import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.AccountStorageUtils;
import io.github.m4gshm.payments.data.BalanceResult;
import io.github.m4gshm.payments.data.LockResult;
import io.github.m4gshm.payments.data.WriteOffException;
import io.github.m4gshm.payments.data.model.Account;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;

import java.util.List;

import static io.github.m4gshm.payments.data.AccountStorageUtils.selectAccountById;
import static io.github.m4gshm.payments.data.AccountStorageUtils.selectForUpdate;
import static io.github.m4gshm.payments.data.AccountStorageUtils.toAccount;
import static io.github.m4gshm.payments.data.AccountStorageUtils.updateAccountAddAmount;
import static io.github.m4gshm.payments.data.AccountStorageUtils.updateAccountLock;
import static io.github.m4gshm.payments.data.AccountStorageUtils.updateAccountLockedAmount;
import static io.github.m4gshm.payments.data.AccountStorageUtils.updateAccountUnlock;
import static io.github.m4gshm.storage.UpdateUtils.checkUpdateCount;
import static io.github.m4gshm.storage.UpdateUtils.notFound;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.ACCOUNT;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountStorageImpl implements AccountStorage {
    @Getter
    Class<Account> entityClass = Account.class;
    DSLContext dsl;

    private static <T> T checkFound(String clientId, T account) {
        if (account == null) {
            throw notFound("account", clientId);
        }
        return account;
    }

    @Override
    public BalanceResult addAmount(String clientId, double replenishment) {
        var accountRecord = checkFound(clientId, updateAccountAddAmount(dsl, clientId, replenishment).fetchOne());
        var balance = accountRecord.get(ACCOUNT.AMOUNT) - accountRecord.get(ACCOUNT.LOCKED);
        if (balance < 0) {
            throw new IllegalStateException("balance overflow " + balance
                    + " with replenishment "
                    +
                    replenishment);
        }
        var timestamp = accountRecord.get(ACCOUNT.UPDATED_AT);
        return BalanceResult.builder().balance(balance).timestamp(timestamp).build();

    }

    @Override
    public LockResult addLock(String clientId, @Positive double amount) {
        var record = checkFound(clientId, selectForUpdate(dsl, clientId).fetchOne());
        double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
        double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
        double newLocked = locked + amount;

        var result = LockResult.builder();
        if (totalAmount < newLocked) {
            return result.success(false).insufficientAmount(newLocked - totalAmount).build();
        } else {
            var count = updateAccountLock(dsl, clientId, amount).execute();
            return checkUpdateCount(count, "account", clientId, () -> result.success(true).build());
        }
    }

    @Override
    public List<Account> findAll() {
        return AccountStorageUtils.selectAccounts(dsl).stream().map(AccountStorageUtils::toAccount).toList();
    }

    @Override
    public Account findById(String clientId) {
        return toAccount(checkFound(clientId, selectAccountById(dsl, clientId).fetchOne()));
    }

    @Override
    public void unlock(String clientId, @Positive double amount) {
        var record = checkFound(clientId, selectForUpdate(dsl, clientId).fetchOne());
        double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
        if (locked == 0) {
            //log
        } else {
            double newLocked = locked - amount;
            if (newLocked < 0) {
                amount = locked;
//            throw new InvalidUnlockFundValueException(clientId, newLocked);
                //log
            }
            var count = updateAccountUnlock(dsl, clientId, amount).execute();
            checkUpdateCount(count, "account", clientId, () -> null);
        }
    }

    @Override
    public BalanceResult writeOff(String clientId, @Positive double amount) {
        var record = checkFound(clientId, selectForUpdate(dsl, clientId).fetchOne());

        double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
        double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
        double newLocked = locked - amount;
        double newAmount = totalAmount - amount;
        if (newLocked < 0 || newAmount < 0) {
            throw new WriteOffException(
                    newLocked < 0 ? -newLocked : 0,
                    newAmount < 0 ? -newAmount : 0
            );
        }
        var count = updateAccountLockedAmount(dsl, clientId, newLocked, newAmount).execute();
        return checkUpdateCount(count,
                "account",
                clientId,
                () -> BalanceResult.builder()
                        .balance(newAmount)
                        .build());
    }
}
