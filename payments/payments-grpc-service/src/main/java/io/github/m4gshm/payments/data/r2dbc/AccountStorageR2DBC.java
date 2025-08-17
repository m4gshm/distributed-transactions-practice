package io.github.m4gshm.payments.data.r2dbc;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.model.Account;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectJoinStep;
import org.jooq.UpdateResultStep;
import org.springframework.stereotype.Service;
import payments.data.access.jooq.tables.records.AccountRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.github.m4gshm.jooq.utils.Query.selectAllFrom;
import static io.github.m4gshm.jooq.utils.Update.checkUpdateCount;
import static io.github.m4gshm.jooq.utils.Update.notFound;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.ACCOUNT;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.just;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountStorageR2DBC implements AccountStorage {
    @Getter
    Class<Account> entityClass = Account.class;

    Jooq jooq;

    public static SelectJoinStep<Record> selectAccounts(DSLContext dsl) {
        return selectAllFrom(dsl, ACCOUNT);
    }

    private static Mono<Record2<Double, Double>> selectForUpdate(DSLContext dsl, String clientId) {
        return from(dsl
                       .select(ACCOUNT.LOCKED, ACCOUNT.AMOUNT)
                       .from(ACCOUNT)
                       .where(ACCOUNT.CLIENT_ID.eq(clientId))
                       .forUpdate());
    }

    public static Account toAccount(Record record) {
        return Account.builder()
                      .clientId(record.get(ACCOUNT.CLIENT_ID))
                      .amount(record.get(ACCOUNT.AMOUNT))
                      .locked(record.get(ACCOUNT.LOCKED))
                      .updatedAt(record.get(ACCOUNT.UPDATED_AT))
                      .build();
    }

    @Override
    public Mono<LockResult> addLock(String clientId, @Positive double amount) {
        return jooq.transactional(dsl -> {
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
                double newLocked = locked + amount;

                var result = LockResult.builder();
                if (totalAmount < newLocked) {
                    return just(result.success(false).insufficientAmount(newLocked - totalAmount).build());
                } else {
                    return from(dsl
                                   .update(ACCOUNT)
                                   .set(ACCOUNT.LOCKED, ACCOUNT.LOCKED.plus(amount))
                                   .where(ACCOUNT.CLIENT_ID.eq(clientId))).flatMap(checkUpdateCount("account",
                                                                                                    clientId,
                                                                                                    () -> result.success(true)
                                                                                                                .build()));
                }
            });
        });
    }

    @Override
    public Mono<List<Account>> findAll() {
        return jooq.transactional(dsl -> {
            return Flux.from(selectAccounts(dsl)).map(AccountStorageR2DBC::toAccount).collectList();
        });
    }

    @Override
    public Mono<Account> findById(String id) {
        return jooq.transactional(dsl -> {
            return from(selectAccounts(dsl).where(ACCOUNT.CLIENT_ID.eq(id))).map(AccountStorageR2DBC::toAccount);
        });
    }

    @Override
    public Mono<BalanceResult> topUp(String clientId, double replenishment) {
        return jooq.transactional(dsl -> {
            UpdateResultStep<AccountRecord> returning = dsl
                                                           .update(ACCOUNT)
                                                           .set(ACCOUNT.AMOUNT, ACCOUNT.AMOUNT.plus(replenishment))
                                                           .where(ACCOUNT.CLIENT_ID.eq(clientId))
                                                           .returning(ACCOUNT.fields());
            return from(returning).map(accountRecord -> {
                var balance = accountRecord.get(ACCOUNT.AMOUNT) - accountRecord.get(ACCOUNT.LOCKED);
                return BalanceResult.builder().balance(balance).build();
            }).switchIfEmpty(notFound("account", clientId));
        });
    }

    @Override
    public Mono<Void> unlock(String clientId, @Positive double amount) {
        return jooq.transactional(dsl -> {
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double newLocked = locked - amount;
                if (newLocked < 0) {
                    return Mono.error(new InvalidUnlockFundValueException(clientId, newLocked));
                } else {
                    return from(dsl
                                   .update(ACCOUNT)
                                   .set(ACCOUNT.LOCKED, ACCOUNT.LOCKED.minus(amount))
                                   .where(ACCOUNT.CLIENT_ID.eq(clientId))).flatMap(checkUpdateCount("account",
                                                                                                    clientId,
                                                                                                    () -> null));
                }
            });
        });
    }

    @Override
    public Mono<BalanceResult> writeOff(String clientId, @Positive double amount) {
        return jooq.transactional(dsl -> {
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
                double newLocked = locked - amount;
                double newAmount = totalAmount - amount;
                if (newLocked < 0 || newAmount < 0) {
                    return error(new WriteOffException(newLocked < 0 ? -newLocked : 0, newAmount < 0 ? -newAmount : 0));
                } else {
                    return from(dsl
                                   .update(ACCOUNT)
                                   .set(ACCOUNT.LOCKED, newLocked)
                                   .set(ACCOUNT.AMOUNT, newAmount)
                                   .where(ACCOUNT.CLIENT_ID.eq(clientId))).flatMap(checkUpdateCount("account",
                                                                                                    clientId,
                                                                                                    () -> {
                                                                                                        return BalanceResult.builder()
                                                                                                                            .balance(newAmount)
                                                                                                                            .build();
                                                                                                    }));
                }
            });
        });
    }
}
