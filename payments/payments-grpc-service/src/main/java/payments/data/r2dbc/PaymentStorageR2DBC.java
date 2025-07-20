package payments.data.r2dbc;

import jooq.utils.TwoPhaseTransaction;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import payments.data.PaymentStorage;
import payments.data.model.Payment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;
import static payments.data.access.jooq.Tables.PAYMENTS;
import static payments.data.r2dbc.PaymentStorageR2DBCUtils.selectPayments;
import static payments.data.r2dbc.PaymentStorageR2DBCUtils.storeRoutine;
import static payments.data.r2dbc.PaymentStorageR2DBCUtils.toPayment;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class PaymentStorageR2DBC implements PaymentStorage {
    DSLContext dsl;

    @Override
    public Mono<List<Payment>> findAll() {
        return Flux.from(selectPayments(dsl)).map(record -> toPayment(record)).collectList();
    }

    @Override
    public Mono<Payment> findById(String id) {
        return from(selectPayments(dsl).where(PAYMENTS.ID.eq(id))).map(record -> toPayment(record));
    }

    @Override
    public Mono<Payment> save(Payment payment, boolean twoPhasedTransaction) {
        return from(dsl.transactionPublisher(trx -> {
            var dsl = trx.dsl();
            var routine = storeRoutine(dsl, payment);
            var id = payment.id();
            return !twoPhasedTransaction ? routine : TwoPhaseTransaction.prepare(routine, dsl, id);
        }));
    }
}
