package io.github.m4gshm.payments.data.r2dbc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.payments.data.model.Payment.Status;

import java.time.OffsetDateTime;

import static java.util.Optional.ofNullable;
import static io.github.m4gshm.jooq.utils.Query.selectAllFrom;
import static payments.data.access.jooq.Tables.PAYMENT;

@Slf4j
@UtilityClass
public class PaymentStorageR2DBCUtils {
    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }

    public static SelectJoinStep<Record> selectPayments(DSLContext dsl) {
        return selectAllFrom(dsl, PAYMENT);
    }

    public static Payment toPayment(Record record) {
        return Payment.builder()
                .id(record.get(PAYMENT.ID))
                .externalRef(record.get(PAYMENT.EXTERNAL_REF))
                .status(Status.byCode(record.get(PAYMENT.STATUS)))
                .amount(record.get(PAYMENT.AMOUNT))
                .clientId(record.get(PAYMENT.CLIENT_ID))
                .createdAt(record.get(PAYMENT.CREATED_AT))
                .updatedAt(record.get(PAYMENT.UPDATED_AT))
                .build();
    }
}
