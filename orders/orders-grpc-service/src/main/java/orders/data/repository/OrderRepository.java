package orders.data.repository;

import orders.data.model.OrderEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface OrderRepository {
    Mono<List<OrderEntity>> findAll();
    Mono<OrderEntity> findById(UUID id);
}
