package io.github.m4gshm.reserve.data;

import io.github.m4gshm.storage.ReadStorage;
import lombok.Builder;
import reactor.core.publisher.Mono;
import io.github.m4gshm.reserve.data.model.WarehouseItem;

import java.util.Collection;
import java.util.List;

public interface WarehouseItemStorage extends ReadStorage<WarehouseItem, String> {

    Mono<List<ReserveItem.Result>> reserve(Collection<ReserveItem> items);

    Mono<List<ReleaseItem.Result>> release(Collection<ReleaseItem> items);

    @Builder(toBuilder = true)
    record ReserveItem(String id, int amount) {
        @Builder
        public record Result(String id, Integer remainder, boolean reserved) {
        }
    }

    @Builder(toBuilder = true)
    record ReleaseItem(String id, int amount) {
        @Builder
        public record Result(String id, Integer remainder) {
        }
    }
}
