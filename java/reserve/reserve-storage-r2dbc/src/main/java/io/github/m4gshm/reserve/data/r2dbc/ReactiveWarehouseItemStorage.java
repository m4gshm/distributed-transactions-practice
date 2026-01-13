package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.reserve.data.model.ItemOp;
import io.github.m4gshm.reserve.data.model.WarehouseItem;
import io.github.m4gshm.storage.ReactiveReadOperations;
import jakarta.validation.constraints.Min;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface ReactiveWarehouseItemStorage extends ReactiveReadOperations<WarehouseItem, String> {

    Mono<List<ItemOp.Result>> cancelReserve(Collection<ItemOp> items);

    Mono<List<ItemOp.Result>> release(Collection<ItemOp> items);

    Mono<List<ItemOp.ReserveResult>> reserve(Collection<ItemOp> items);

    Mono<ItemOp.Result> topUp(String id, @Min(1) int amount);

}
