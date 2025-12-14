package io.github.m4gshm.payments.data.r2dbc;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;

import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.storage.jooq.Query;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import payments.data.access.jooq.Tables;

@Slf4j
@UtilityClass
public class PaymentStorageR2DBCUtils {

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
}
