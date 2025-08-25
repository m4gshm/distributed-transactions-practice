package io.github.m4gshm.reserve.data;

import java.util.Collection;
import java.util.List;

import io.github.m4gshm.reserve.data.model.WarehouseItem;
import io.github.m4gshm.storage.ReadOperations;
import lombok.Builder;
import reactor.core.publisher.Mono;

public interface WarehouseItemStorage extends ReadOperations<WarehouseItem, String> {

    Mono<List<ItemOp.ReserveResult>> reserve(Collection<ItemOp> items);

    Mono<List<ItemOp.Result>> cancelReserve(Collection<ItemOp> items);

    Mono<List<ItemOp.Result>> release(Collection<ItemOp> items);

    Mono<ItemOp.Result> topUp(String id, int amount);

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
