package payments.data;

import payments.data.model.Payment;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PaymentStorage {
    Mono<List<Payment>> findAll();
    Mono<Payment> findById(String id);
    Mono<Payment> save(Payment order, boolean twoPhasedTransaction);
}
