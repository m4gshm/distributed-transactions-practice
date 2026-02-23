package io.github.m4gshm.payments.data;

import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.storage.jooq.Query;
import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectConditionStep;
import org.jooq.SelectForUpdateOfStep;
import org.jooq.SelectJoinStep;
import org.jooq.UpdateConditionStep;
import org.jooq.UpdateResultStep;
import org.jooq.UpdateSetMoreStep;
import payments.data.access.jooq.tables.records.AccountRecord;

import java.time.OffsetDateTime;

import static payments.data.access.jooq.Tables.ACCOUNT;

@UtilityClass
public class AccountStorageUtils {
    public static SelectConditionStep<Record> selectAccountById(DSLContext dsl, String clientId) {
        return selectAccounts(dsl).where(ACCOUNT.CLIENT_ID.eq(clientId));
    }

    public static SelectJoinStep<Record> selectAccounts(DSLContext dsl) {
        return Query.selectAllFrom(dsl, ACCOUNT);
    }

    public static SelectForUpdateOfStep<Record2<Double, Double>> selectForUpdate(DSLContext dsl, String clientId) {
        return dsl
                .select(ACCOUNT.LOCKED, ACCOUNT.AMOUNT)
                .from(ACCOUNT)
                .where(ACCOUNT.CLIENT_ID.eq(clientId))
                .orderBy(ACCOUNT.CLIENT_ID)
                .forNoKeyUpdate();
    }

    public static Account toAccount(Record record) {
        return Account.builder()
                .clientId(record.get(ACCOUNT.CLIENT_ID))
                .amount(record.get(ACCOUNT.AMOUNT))
                .locked(record.get(ACCOUNT.LOCKED))
                .updatedAt(record.get(ACCOUNT.UPDATED_AT))
                .build();
    }

    public static UpdateSetMoreStep<AccountRecord> updateAccount(DSLContext dsl) {
        return dsl.update(ACCOUNT).set(ACCOUNT.UPDATED_AT, OffsetDateTime.now());
    }

    public static UpdateResultStep<AccountRecord> updateAccountAddAmount(DSLContext dsl,
                                                                         String clientId,
                                                                         double replenishment) {
        return updateAccount(dsl)
                .set(ACCOUNT.AMOUNT, ACCOUNT.AMOUNT.plus(replenishment))
                .where(ACCOUNT.CLIENT_ID.eq(clientId))
                .returning(ACCOUNT.fields());
    }

    public static UpdateConditionStep<AccountRecord> updateAccountLock(DSLContext dsl, String clientId, double amount) {
        return updateAccount(dsl)
                .set(ACCOUNT.LOCKED, ACCOUNT.LOCKED.plus(amount))
                .where(ACCOUNT.CLIENT_ID.eq(clientId));
    }

    public static UpdateConditionStep<AccountRecord> updateAccountLockedAmount(
                                                                               DSLContext dsl,
                                                                               String clientId,
                                                                               double newLocked,
                                                                               double newAmount
    ) {
        return updateAccount(dsl)
                .set(ACCOUNT.LOCKED, newLocked)
                .set(ACCOUNT.AMOUNT, newAmount)
                .where(ACCOUNT.CLIENT_ID.eq(clientId));
    }

    public static UpdateConditionStep<AccountRecord> updateAccountUnlock(DSLContext dsl,
                                                                         String clientId,
                                                                         double amount) {
        return updateAccount(dsl)
                .set(ACCOUNT.LOCKED, ACCOUNT.LOCKED.minus(amount))
                .where(ACCOUNT.CLIENT_ID.eq(clientId));
    }
}
