package io.github.m4gshm.payments.data.r2dbc;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.payments.data.AccountStorageUtils;
import io.github.m4gshm.payments.data.InvalidUnlockFundValueException;
import io.github.m4gshm.payments.data.ReactiveAccountStorage;
import io.github.m4gshm.payments.data.WriteOffException;
import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.jooq.ReactiveUpdateUtils;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.ACCOUNT;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactiveAccountStorageR2dbc implements ReactiveAccountStorage {
    @Getter
    Class<Account> entityClass = Account.class;

    ReactiveJooq jooq;

    @Override
    public Mono<BalanceResult> addAmount(String clientId, double replenishment) {
        return jooq.inTransaction(dsl -> {
            return from(AccountStorageUtils.updateAccountAddAmount(dsl, clientId, replenishment)).map(accountRecord -> {
                var balance = accountRecord.get(ACCOUNT.AMOUNT) - accountRecord.get(ACCOUNT.LOCKED);
                if (balance < 0) {
                    throw new IllegalStateException("balance overflow " + balance
                            + " with replenishment "
                            +
                            replenishment);
                }
                var timestamp = accountRecord.get(ACCOUNT.UPDATED_AT);
                return BalanceResult.builder().balance(balance).timestamp(timestamp).build();
            }).switchIfEmpty(ReactiveUpdateUtils.notFound("account", clientId));
        });
    }

    @Override
    public Mono<LockResult> addLock(String clientId, @Positive double amount) {
        return jooq.inTransaction(dsl -> {
            return Mono.from(AccountStorageUtils.selectForUpdate(dsl, clientId)).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
                double newLocked = locked + amount;

                var result = LockResult.builder();
                if (totalAmount < newLocked) {
                    return Mono.just(result.success(false).insufficientAmount(newLocked - totalAmount).build());
                } else {
                    return from(AccountStorageUtils.updateAccountLock(dsl, clientId, amount)).flatMap(
                            ReactiveUpdateUtils.checkUpdateCount(
                                    "account",
                                    clientId,
                                    () -> result.success(true).build()
                            ));
                }
            });
        });
    }

    @Override
    public Mono<List<Account>> findAll() {
        return jooq.inTransaction(dsl -> {
            return Flux.from(AccountStorageUtils.selectAccounts(dsl)).map(AccountStorageUtils::toAccount).collectList();
        });
    }

    @Override
    public Mono<Account> findById(String id) {
        return jooq.inTransaction(dsl -> {
            return from(AccountStorageUtils.selectAccountById(dsl, id))
                    .map(AccountStorageUtils::toAccount);
        });
    }

    @Override
    public Mono<Void> unlock(String clientId, @Positive double amount) {
        return jooq.inTransaction(dsl -> {
            return Mono.from(AccountStorageUtils.selectForUpdate(dsl, clientId)).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double newLocked = locked - amount;
                if (newLocked < 0) {
                    return error(new InvalidUnlockFundValueException(clientId, newLocked));
                } else {
                    return from(AccountStorageUtils.updateAccountUnlock(dsl, clientId, amount))
                            .flatMap(ReactiveUpdateUtils.checkUpdateCount(
                                    "account",
                                    clientId,
                                    () -> null
                            ));
                }
            });
        });
    }

    @Override
    public Mono<BalanceResult> writeOff(String clientId, @Positive double amount) {
        return jooq.inTransaction(dsl -> Mono.from(AccountStorageUtils.selectForUpdate(dsl, clientId))
                .flatMap(record -> {
                    double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                    double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
                    double newLocked = locked - amount;
                    double newAmount = totalAmount - amount;
                    if (newLocked < 0 || newAmount < 0) {
                        return error(new WriteOffException(
                                newLocked < 0 ? -newLocked : 0,
                                newAmount < 0 ? -newAmount : 0
                        ));
                    }
                    return from(AccountStorageUtils.updateAccountLockedAmount(dsl, clientId, newLocked, newAmount))
                            .flatMap(ReactiveUpdateUtils.checkUpdateCount(
                                    "account",
                                    clientId,
                                    () -> BalanceResult.builder()
                                            .balance(newAmount)
                                            .build()
                            ));
                }));
    }
}
