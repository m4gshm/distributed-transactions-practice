package io.github.m4gshm.payments.data.r2dbc;

import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.from;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectJoinStep;
import org.jooq.UpdateResultStep;
import org.springframework.stereotype.Service;

import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.jooq.Query;
import io.github.m4gshm.storage.jooq.Update;
import io.github.m4gshm.utils.Jooq;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import payments.data.access.jooq.Tables;
import payments.data.access.jooq.tables.records.AccountRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountStorageR2DBC implements AccountStorage {
    @Getter
    Class<Account> entityClass = Account.class;

    Jooq jooq;

    public static SelectJoinStep<Record> selectAccounts(DSLContext dsl) {
        return Query.selectAllFrom(dsl, Tables.ACCOUNT);
    }

    private static Mono<Record2<Double, Double>> selectForUpdate(DSLContext dsl, String clientId) {
        return from(dsl
                .select(Tables.ACCOUNT.LOCKED, Tables.ACCOUNT.AMOUNT)
                .from(Tables.ACCOUNT)
                .where(Tables.ACCOUNT.CLIENT_ID.eq(clientId))
                .forUpdate());
    }

    public static Account toAccount(Record record) {
        return Account.builder()
                .clientId(record.get(Tables.ACCOUNT.CLIENT_ID))
                .amount(record.get(Tables.ACCOUNT.AMOUNT))
                .locked(record.get(Tables.ACCOUNT.LOCKED))
                .updatedAt(record.get(Tables.ACCOUNT.UPDATED_AT))
                .build();
    }

    @Override
    public Mono<BalanceResult> addAmount(String clientId, double replenishment) {
        return jooq.inTransaction(dsl -> {
            UpdateResultStep<AccountRecord> returning = dsl
                    .update(Tables.ACCOUNT)
                    .set(Tables.ACCOUNT.AMOUNT, Tables.ACCOUNT.AMOUNT.plus(replenishment))
                    .where(Tables.ACCOUNT.CLIENT_ID.eq(clientId))
                    .returning(Tables.ACCOUNT.fields());
            return from(returning).map(accountRecord -> {
                var balance = accountRecord.get(Tables.ACCOUNT.AMOUNT) - accountRecord.get(Tables.ACCOUNT.LOCKED);
                return BalanceResult.builder().balance(balance).build();
            }).switchIfEmpty(Update.notFound("account", clientId));
        });
    }

    @Override
    public Mono<LockResult> addLock(String clientId, @Positive double amount) {
        return jooq.inTransaction(dsl -> {
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(Tables.ACCOUNT.LOCKED)).orElse(0.0);
                double totalAmount = ofNullable(record.get(Tables.ACCOUNT.AMOUNT)).orElse(0.0);
                double newLocked = locked + amount;

                var result = LockResult.builder();
                if (totalAmount < newLocked) {
                    return Mono.just(result.success(false).insufficientAmount(newLocked - totalAmount).build());
                } else {
                    return from(dsl
                            .update(Tables.ACCOUNT)
                            .set(Tables.ACCOUNT.LOCKED, Tables.ACCOUNT.LOCKED.plus(amount))
                            .where(Tables.ACCOUNT.CLIENT_ID.eq(clientId)))
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
            return from(selectAccounts(dsl).where(Tables.ACCOUNT.CLIENT_ID.eq(id)))
                    .map(AccountStorageR2DBC::toAccount);
        });
    }

    @Override
    public Mono<Void> unlock(String clientId, @Positive double amount) {
        return jooq.inTransaction(dsl -> {
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(Tables.ACCOUNT.LOCKED)).orElse(0.0);
                double newLocked = locked - amount;
                if (newLocked < 0) {
                    return error(new InvalidUnlockFundValueException(clientId, newLocked));
                } else {
                    return from(dsl
                            .update(Tables.ACCOUNT)
                            .set(Tables.ACCOUNT.LOCKED, Tables.ACCOUNT.LOCKED.minus(amount))
                            .where(Tables.ACCOUNT.CLIENT_ID.eq(clientId)))
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
            double locked = ofNullable(record.get(Tables.ACCOUNT.LOCKED)).orElse(0.0);
            double totalAmount = ofNullable(record.get(Tables.ACCOUNT.AMOUNT)).orElse(0.0);
            double newLocked = locked - amount;
            double newAmount = totalAmount - amount;
            return newLocked < 0 || newAmount < 0 ? error(new WriteOffException(
                    newLocked < 0 ? -newLocked : 0,
                    newAmount < 0 ? -newAmount : 0
            ))
                    : from(dsl
                            .update(Tables.ACCOUNT)
                            .set(Tables.ACCOUNT.LOCKED, newLocked)
                            .set(Tables.ACCOUNT.AMOUNT, newAmount)
                            .where(Tables.ACCOUNT.CLIENT_ID.eq(clientId)))
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
