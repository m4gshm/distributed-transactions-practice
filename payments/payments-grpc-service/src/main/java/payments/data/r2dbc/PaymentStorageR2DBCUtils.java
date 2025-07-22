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
import static payments.data.access.jooq.Tables.PAYMENT;
import static reactor.core.publisher.Mono.from;

@Slf4j
@UtilityClass
public class PaymentStorageR2DBCUtils {
    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
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

    public static Mono<Payment> storeRoutine(DSLContext dsl, Payment payment) {
        return from(dsl.insertInto(PAYMENT)
                .set(PAYMENT.ID, payment.id())
                .set(PAYMENT.CREATED_AT, orNow(payment.createdAt()))
                .set(PAYMENT.EXTERNAL_REF, payment.externalRef())
                .set(PAYMENT.CLIENT_ID, payment.clientId())
                .set(PAYMENT.STATUS, payment.status().getCode())
                .set(PAYMENT.AMOUNT, payment.amount())
                .onDuplicateKeyUpdate()
                .set(PAYMENT.UPDATED_AT, orNow(payment.updatedAt()))
                .set(PAYMENT.STATUS, payment.status().getCode())
                .set(PAYMENT.AMOUNT, payment.amount())).map(count -> {
            log.debug("stored payment rows {}", count);
            return payment;
        });
    }

    public static SelectJoinStep<Record> selectPayments(DSLContext dsl) {
        return selectAllFrom(dsl, PAYMENT);
    }
}
