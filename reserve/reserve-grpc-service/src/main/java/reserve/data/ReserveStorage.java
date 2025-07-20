package reserve.data;

import reactor.core.publisher.Mono;
import reserve.data.model.Reserve;

import java.util.List;

public interface ReserveStorage {
    Mono<List<Reserve>> findAll();
    Mono<Reserve> findById(String id);
    Mono<Reserve> save(Reserve order, boolean twoPhasedTransaction);
}
