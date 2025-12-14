package io.github.m4gshm.payments.data.r2dbc;

import static io.github.m4gshm.DateTimeUtils.orNow;
import static io.github.m4gshm.payments.data.r2dbc.PaymentStorageR2DBCUtils.selectPayments;
import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.PAYMENT;

import java.util.List;

import io.github.m4gshm.DateTimeUtils;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Payment;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
            return Mono.from(selectPayments(dsl).where(PAYMENT.ID.eq(id)))
                    .map(PaymentStorageR2DBCUtils::toPayment);
        });
    }

    @Override
    public Mono<Payment> save(@Valid Payment payment) {
        return jooq.inTransaction(dsl -> {
            return Mono.from(dsl.insertInto(PAYMENT)
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
                    .set(PAYMENT.INSUFFICIENT, DSL.excluded(PAYMENT.INSUFFICIENT)))
                    .map(count -> {
                        log.debug("stored payment rows {}", count);
                        return payment;
                    });
        });
    }
}
