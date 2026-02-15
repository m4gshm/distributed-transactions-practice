package io.github.m4gshm.payments.data.jdbc;

import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.PaymentStorageUtils;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.storage.UpdateUtils;
import io.micrometer.observation.annotation.Observed;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static io.github.m4gshm.payments.data.PaymentStorageUtils.selectPaymentById;
import static io.github.m4gshm.payments.data.PaymentStorageUtils.upsertPayment;
import static io.github.m4gshm.storage.UpdateUtils.notFound;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Validated
@Observed
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class PaymentStorageImpl implements PaymentStorage {
    @Getter
    Class<Payment> entityClass = Payment.class;
    DSLContext dsl;

    private static <T> T checkFound(String id, T payment) {
        if (payment == null) {
            throw notFound("payment", id);
        }
        return payment;
    }

    @Override
    public List<Payment> findAll() {
        return PaymentStorageUtils.selectPayments(dsl).stream().map(PaymentStorageUtils::toPayment).toList();
    }

    @Override
    public Payment findById(String id) {
        var record = selectPaymentById(id, dsl).fetchOne();
        return PaymentStorageUtils.toPayment(checkFound(id, record));
    }

    @Override
    public Payment save(@Valid Payment payment) {
        var count = upsertPayment(payment, dsl).execute();
//        log.debug("stored payment rows {}", count);
        return UpdateUtils.checkUpdateCount(count, "payment", payment.id(), () -> payment);
    }
}
