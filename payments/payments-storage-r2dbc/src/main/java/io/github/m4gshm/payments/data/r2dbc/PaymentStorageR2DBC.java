package io.github.m4gshm.payments.data.r2dbc;

import static io.github.m4gshm.payments.data.r2dbc.PaymentStorageR2DBCUtils.selectPayments;
import static lombok.AccessLevel.PRIVATE;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.utils.Jooq;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import payments.data.access.jooq.Tables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class PaymentStorageR2DBC implements PaymentStorage {
    @Getter
    private final Class<Payment> entityClass = Payment.class;
    Jooq jooq;

    @Override
    public Mono<List<Payment>> findAll() {
        return jooq.inTransaction(dsl -> {
            return Flux.from(selectPayments(dsl)).map(PaymentStorageR2DBCUtils::toPayment).collectList();
        });
    }

    @Override
    public Mono<Payment> findById(String id) {
        return jooq.inTransaction(dsl -> {
            return Mono.from(selectPayments(dsl).where(Tables.PAYMENT.ID.eq(id)))
                    .map(PaymentStorageR2DBCUtils::toPayment);
        });
    }

    @Override
    public Mono<Payment> save(@Valid Payment payment) {
        return jooq.inTransaction(dsl -> {
            return Mono.from(dsl.insertInto(Tables.PAYMENT)
                    .set(Tables.PAYMENT.ID, payment.id())
                    .set(Tables.PAYMENT.CREATED_AT, PaymentStorageR2DBCUtils.orNow(payment.createdAt()))
                    .set(Tables.PAYMENT.EXTERNAL_REF, payment.externalRef())
                    .set(Tables.PAYMENT.CLIENT_ID, payment.clientId())
                    .set(Tables.PAYMENT.STATUS, payment.status().getCode())
                    .set(Tables.PAYMENT.AMOUNT, payment.amount())
                    .set(Tables.PAYMENT.INSUFFICIENT, payment.insufficient())
                    .onDuplicateKeyUpdate()
                    .set(Tables.PAYMENT.UPDATED_AT, PaymentStorageR2DBCUtils.orNow(payment.updatedAt()))
                    .set(Tables.PAYMENT.STATUS, payment.status().getCode())
                    .set(Tables.PAYMENT.AMOUNT, payment.amount())
                    .set(Tables.PAYMENT.INSUFFICIENT, payment.insufficient()))
                    .map(count -> {
                        log.debug("stored payment rows {}", count);
                        return payment;
                    });
        });
    }
}
