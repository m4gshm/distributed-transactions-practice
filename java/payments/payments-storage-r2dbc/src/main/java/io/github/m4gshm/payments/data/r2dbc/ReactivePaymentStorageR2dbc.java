package io.github.m4gshm.payments.data.r2dbc;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.payments.data.PaymentStorageUtils;
import io.github.m4gshm.payments.data.ReactivePaymentStorage;
import io.github.m4gshm.payments.data.model.Payment;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.github.m4gshm.payments.data.PaymentStorageUtils.selectPaymentById;
import static io.github.m4gshm.payments.data.PaymentStorageUtils.selectPayments;
import static io.github.m4gshm.payments.data.PaymentStorageUtils.upsertPayment;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Validated
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactivePaymentStorageR2dbc implements ReactivePaymentStorage {
    @Getter
    private final Class<Payment> entityClass = Payment.class;
    ReactiveJooq jooq;

    private static String getOp(String op) {
        return Payment.class.getSimpleName() + ":" + op;
    }

    @Override
    public Mono<List<Payment>> findAll() {
        return jooq.supportTransaction(getOp("findAll"), dsl -> {
            return Flux.from(selectPayments(dsl)).map(PaymentStorageUtils::toPayment).collectList();
        });
    }

    @Override
    public Mono<Payment> findById(String id) {
        return jooq.supportTransaction(getOp("findById"), dsl -> {
            return Mono.from(selectPaymentById(id, dsl)).map(PaymentStorageUtils::toPayment);
        });
    }

    @Override
    public Mono<Payment> save(@Valid Payment payment) {
        return jooq.supportTransaction(getOp("save"), dsl -> {
            return Mono.from(upsertPayment(payment, dsl)).map(count -> {
//                        log.debug("stored payment rows {}", count);
                return payment;
            });
        });
    }
}
