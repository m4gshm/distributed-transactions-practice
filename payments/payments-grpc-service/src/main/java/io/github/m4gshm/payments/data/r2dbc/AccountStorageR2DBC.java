package io.github.m4gshm.payments.data.r2dbc;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.model.Account;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.github.m4gshm.jooq.utils.Query.selectAllFrom;
import static io.github.m4gshm.jooq.utils.Update.checkUpdate;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.ACCOUNT;
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

    public static Account toAccount(org.jooq.Record record) {
        return Account.builder()
                .clientId(record.get(ACCOUNT.CLIENT_ID))
                .amount(record.get(ACCOUNT.AMOUNT))
                .locked(record.get(ACCOUNT.LOCKED))
                .updatedAt(record.get(ACCOUNT.UPDATED_AT))
                .build();
    }

    public static SelectJoinStep<Record> selectAccounts(DSLContext dsl) {
        return selectAllFrom(dsl, ACCOUNT);
    }

    private static Mono<Record2<Double, Double>> selectForUpdate(DSLContext dsl, String clientId) {
        return from(dsl
                .select(ACCOUNT.LOCKED, ACCOUNT.AMOUNT)
                .from(ACCOUNT)
                .where(ACCOUNT.CLIENT_ID.eq(clientId))
                .forUpdate()
        );
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
    public Mono<LockResult> addLock(Account account, double amount) {
        return jooq.transactional(dsl -> {
            var clientId = account.clientId();
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
                            .set(ACCOUNT.LOCKED, newLocked)
                            .where(ACCOUNT.CLIENT_ID.eq(clientId))
                    ).flatMap(checkUpdate("account", clientId, () -> result.success(true).build()));
                }
            });
        });
    }

    @Override
    public Mono<WriteOffResult> writeOff(Account account, Double amount) {
        return jooq.transactional(dsl -> {
            var clientId = account.clientId();
            return selectForUpdate(dsl, clientId).flatMap(record -> {
                double locked = ofNullable(record.get(ACCOUNT.LOCKED)).orElse(0.0);
                double totalAmount = ofNullable(record.get(ACCOUNT.AMOUNT)).orElse(0.0);
                double newLocked = locked - amount;
                double newAmount = totalAmount - amount;
                var result = WriteOffResult.builder();
                if (newLocked < 0) {
                    return just(result.success(false).insufficientHold(-newLocked).build());
                } else if (newAmount < 0) {
                    return just(result.success(false).insufficientAmount(-newAmount).build());
                } else {
                    return from(dsl
                            .update(ACCOUNT)
                            .set(ACCOUNT.LOCKED, newLocked)
                            .set(ACCOUNT.AMOUNT, newAmount)
                            .where(ACCOUNT.CLIENT_ID.eq(clientId))
                    ).flatMap(checkUpdate("account", clientId, () -> result.success(true)
                            .balance(newAmount).build()));
                }
            });
        });
    }
}
