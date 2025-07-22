package payments.data.r2dbc;

import jooq.utils.TwoPhaseTransaction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;
import payments.data.AccountStorage;
import payments.data.model.Account;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static java.lang.Boolean.TRUE;
import static jooq.utils.Query.selectAllFrom;
import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.ACCOUNT;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AccountStorageR2DBC implements AccountStorage {
    @Getter
    Class<Account> entityClass = Account.class;
    DSLContext dsl;

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

    @Override
    public Mono<List<Account>> findAll() {
        return Flux.from(selectAccounts(dsl)).map(AccountStorageR2DBC::toAccount).collectList();
    }

    @Override
    public Mono<Account> findById(String id) {
        return from(selectAccounts(dsl).where(ACCOUNT.CLIENT_ID.eq(id))).map(AccountStorageR2DBC::toAccount);
    }

    @Override
    public Mono<Boolean> addLock(Account account, Double amount, String txid, boolean twoPhaseCommit) {
        var lockRoutine = from(
                dsl.update(ACCOUNT)
                        .set(ACCOUNT.LOCKED, ACCOUNT.LOCKED.plus(amount))
                        .where(
                                ACCOUNT.CLIENT_ID.eq(account.clientId())
                                        .and(ACCOUNT.AMOUNT.greaterOrEqual(ACCOUNT.LOCKED.plus(amount)))
                        )
        ).map(count -> {
            log.debug("lock account result count, clientId:{}, rows:{}", account.clientId(), count);
            return count > 0;
        }).flatMap(success -> {
            return TRUE.equals(success) ? Mono.just(true) : Mono.empty();
        });
        return !twoPhaseCommit ? lockRoutine : TwoPhaseTransaction.prepare(lockRoutine, dsl, txid);
    }
}
