package io.github.m4gshm.payments.data;

import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.storage.jooq.Query;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import payments.data.access.jooq.Tables;
import payments.data.access.jooq.tables.records.PaymentRecord;

import static io.github.m4gshm.DateTimeUtils.orNow;
import static payments.data.access.jooq.Tables.PAYMENT;

@Slf4j
@UtilityClass
public class PaymentStorageUtils {

    public static SelectConditionStep<Record> selectPaymentById(String id, DSLContext dsl) {
        return selectPayments(dsl).where(PAYMENT.ID.eq(id));
    }

    public static SelectJoinStep<Record> selectPayments(DSLContext dsl) {
        return Query.selectAllFrom(dsl, Tables.PAYMENT);
    }

    public static Payment toPayment(Record record) {
        return Payment.builder()
                .id(record.get(Tables.PAYMENT.ID))
                .externalRef(record.get(Tables.PAYMENT.EXTERNAL_REF))
                .status(record.get(Tables.PAYMENT.STATUS))
                .amount(record.get(Tables.PAYMENT.AMOUNT))
                .clientId(record.get(Tables.PAYMENT.CLIENT_ID))
                .createdAt(record.get(Tables.PAYMENT.CREATED_AT))
                .updatedAt(record.get(Tables.PAYMENT.UPDATED_AT))
                .build();
    }

    public static InsertOnDuplicateSetMoreStep<PaymentRecord> upsertPayment(Payment payment, DSLContext dsl) {
        return dsl.insertInto(PAYMENT)
                .set(PAYMENT.ID, payment.id())
                .set(PAYMENT.CREATED_AT, orNow(payment.createdAt()))
                .set(PAYMENT.EXTERNAL_REF, payment.externalRef())
                .set(PAYMENT.CLIENT_ID, payment.clientId())
                .set(PAYMENT.STATUS, payment.status())
                .set(PAYMENT.AMOUNT, payment.amount())
                .set(PAYMENT.INSUFFICIENT, payment.insufficient())
                .onDuplicateKeyUpdate()
                .set(PAYMENT.UPDATED_AT, orNow(payment.updatedAt()))
                .set(PAYMENT.STATUS, DSL.excluded(PAYMENT.STATUS))
                .set(PAYMENT.AMOUNT, DSL.excluded(PAYMENT.AMOUNT))
                .set(PAYMENT.INSUFFICIENT, DSL.excluded(PAYMENT.INSUFFICIENT));
    }
}
