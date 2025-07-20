package payments.data.r2dbc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import payments.data.model.Payment;
import payments.data.model.Payment.Status;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

import static java.util.Optional.ofNullable;
import static jooq.utils.Query.selectAllFrom;
import static payments.data.access.jooq.Tables.PAYMENTS;
import static reactor.core.publisher.Mono.from;

@Slf4j
@UtilityClass
public class PaymentStorageR2DBCUtils {
    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }

    public static Payment toPayment(Record record) {
        return Payment.builder()
                .id(record.get(PAYMENTS.ID))
                .externalRef(record.get(PAYMENTS.EXTERNAL_REF))
                .status(Status.byCode(record.get(PAYMENTS.STATUS)))
                .amount(record.get(PAYMENTS.AMOUNT))
                .createdAt(record.get(PAYMENTS.CREATED_AT))
                .updatedAt(record.get(PAYMENTS.UPDATED_AT))
                .build();
    }

    public static Mono<Payment> storeRoutine(DSLContext dsl, Payment payment) {
        return from(dsl.insertInto(PAYMENTS)
                .set(PAYMENTS.ID, payment.id())
                .set(PAYMENTS.CREATED_AT, orNow(payment.createdAt()))
                .set(PAYMENTS.EXTERNAL_REF, payment.externalRef())
                .set(PAYMENTS.STATUS, payment.status().getCode())
                .set(PAYMENTS.AMOUNT, payment.amount())
                .onDuplicateKeyUpdate()
                .set(PAYMENTS.UPDATED_AT, orNow(payment.updatedAt()))
                .set(PAYMENTS.STATUS, payment.status().getCode())
                .set(PAYMENTS.AMOUNT, payment.amount())).map(count -> {
            log.debug("stored payment rows {}", count);
            return payment;
        });
    }

    public static SelectJoinStep<Record> selectPayments(DSLContext dsl) {
        return selectAllFrom(dsl, PAYMENTS);
    }
}
