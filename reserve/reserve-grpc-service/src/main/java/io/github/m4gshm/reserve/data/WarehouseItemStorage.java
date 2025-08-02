package io.github.m4gshm.reserve.data;

import io.github.m4gshm.storage.ReadStorage;
import lombok.Builder;
import reactor.core.publisher.Mono;
import io.github.m4gshm.reserve.data.model.WarehouseItem;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface WarehouseItemStorage extends ReadStorage<WarehouseItem, String> {

    Mono<Map<String,Double>> getUnitsCost(Collection<String> ids);

    Mono<List<ReserveItem.Result>> reserve(Collection<ReserveItem> reserves, String txid);

    @Builder(toBuilder = true)
    record ReserveItem(String id, int amount) {
        @Builder
        public record Result(String id, Integer remainder, Status status) {
            public enum Status {
                reserved,
                insufficient_quantity
            }
        }
    }
}
