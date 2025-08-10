package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.WarehouseItem;
import io.github.m4gshm.storage.ReadStorage;
import lombok.Builder;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface WarehouseItemStorage extends ReadStorage<WarehouseItem, String> {

    Mono<List<ItemOp.ReserveResult>> reserve(Collection<ItemOp> items);

    Mono<List<ItemOp.Result>> cancelReserve(Collection<ItemOp> items);

    Mono<List<ItemOp.Result>> release(Collection<ItemOp> items);

    @Builder(toBuilder = true)
    record ItemOp(String id, int amount) {
        @Builder
        public record ReserveResult(String id, Integer remainder, boolean reserved) {
        }

        @Builder
        public record Result(String id, Integer remainder) {
        }
    }

}
