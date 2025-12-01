package io.github.m4gshm.payments.data.r2dbc;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.jooq.Query;
import io.github.m4gshm.storage.jooq.Update;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectJoinStep;
import org.jooq.UpdateSetMoreStep;
import org.springframework.stereotype.Service;
import payments.data.access.jooq.tables.records.AccountRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.ACCOUNT;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountStorageR2DBC implements AccountStorage {
    @Getter
    Class<Account> entityClass = Account.class;

    Jooq jooq;

    public static SelectJoinStep<Record> selectAccounts(DSLContext dsl) {
        return Query.selectAllFrom(dsl, ACCOUNT);
    }

    private static Mono<Record2<Double, Double>> selectForUpdate(DSLContext dsl, String clientId) {
        return from(dsl
                .select(ACCOUNT.LOCKED, ACCOUNT.AMOUNT)
                .from(ACCOUNT)
                .where(ACCOUNT.CLIENT_ID.eq(clientId))
                .orderBy(ACCOUNT.CLIENT_ID)
                .forNoKeyUpdate());
    }

    public static Account toAccount(Record record) {
        return Account.builder()
                .clientId(record.get(ACCOUNT.CLIENT_ID))
                .amount(record.get(ACCOUNT.AMOUNT))
                .locked(record.get(ACCOUNT.LOCKED))
                .updatedAt(record.get(ACCOUNT.UPDATED_AT))
                .build();
    }

    private static UpdateSetMoreStep<AccountRecord> updateAccount(DSLContext dsl) {
        return dsl.update(ACCOUNT).set(ACCOUNT.UPDATED_AT, OffsetDateTime.now());
    }

    @Override
    public Mono<BalanceResult> addAmount(String clientId, double replenishment) {
        return jooq.inTransaction(dsl -> {
            return from(updateAccount(dsl)
                    .set(ACCOUNT.AMOUNT, ACCOUNT.AMOUNT.plus(replenishment))
                    .where(ACCOUNT.CLIENT_ID.eq(clientId))
                    .returning(ACCOUNT.fields())).map(accountRecord -> {
                        var balance = accountRecord.get(ACCOUNT.AMOUNT) - accountRecord.get(ACCOUNT.LOCKED);
                        if (balance < 0) {
                            throw new IllegalStateException("balance overflow " + balance
                                    + " with replenishment "
                                    +
                                    replenishment);
                        }
                        var timestamp = accountRecord.get(ACCOUNT.UPDATED_AT);
                        return BalanceResult.builder().balance(balance).timestamp(timestamp).build();
                    }).switchIfEmpty(Update.notFound("account", clientId));
        });
    }

    @Override
    public Mono<LockResult> addLock(String clientId, @Positive double amount) {
        return jooq.inTransaction(dsl -> {
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
                double newLocked = locked + amount;

                var result = LockResult.builder();
                if (totalAmount < newLocked) {
                    return Mono.just(result.success(false).insufficientAmount(newLocked - totalAmount).build());
                } else {
                    return from(updateAccount(dsl)
                            .set(ACCOUNT.LOCKED, ACCOUNT.LOCKED.plus(amount))
                            .where(ACCOUNT.CLIENT_ID.eq(clientId)))
                            .flatMap(Update.checkUpdateCount(
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
            return Flux.from(selectAccounts(dsl)).map(AccountStorageR2DBC::toAccount).collectList();
        });
    }

    @Override
    public Mono<Account> findById(String id) {
        return jooq.inTransaction(dsl -> {
            return from(selectAccounts(dsl).where(ACCOUNT.CLIENT_ID.eq(id)))
                    .map(AccountStorageR2DBC::toAccount);
        });
    }

    @Override
    public Mono<Void> unlock(String clientId, @Positive double amount) {
        return jooq.inTransaction(dsl -> {
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double newLocked = locked - amount;
                if (newLocked < 0) {
                    return error(new InvalidUnlockFundValueException(clientId, newLocked));
                } else {
                    return from(updateAccount(dsl)
                            .set(ACCOUNT.LOCKED, ACCOUNT.LOCKED.minus(amount))
                            .where(ACCOUNT.CLIENT_ID.eq(clientId)))
                            .flatMap(Update.checkUpdateCount(
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
        return jooq.inTransaction(dsl -> selectForUpdate(dsl, clientId).flatMap(record -> {
            double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
            double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
            double newLocked = locked - amount;
            double newAmount = totalAmount - amount;
            return newLocked < 0 || newAmount < 0 ? error(new WriteOffException(
                    newLocked < 0 ? -newLocked : 0,
                    newAmount < 0 ? -newAmount : 0
            ))
                    : from(updateAccount(dsl)
                            .set(ACCOUNT.LOCKED, newLocked)
                            .set(ACCOUNT.AMOUNT, newAmount)
                            .where(ACCOUNT.CLIENT_ID.eq(clientId)))
                            .flatMap(Update.checkUpdateCount(
                                    "account",
                                    clientId,
                                    () -> BalanceResult.builder()
                                            .balance(newAmount)
                                            .build()
                            ));
        }));
    }
}
