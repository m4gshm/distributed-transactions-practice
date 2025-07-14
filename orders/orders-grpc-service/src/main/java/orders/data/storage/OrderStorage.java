package orders.data.storage;

import orders.data.model.Order;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface OrderStorage {
    Mono<List<Order>> findAll();
    Mono<Order> findById(String id);

    Mono<Order> save(Order order);
}
