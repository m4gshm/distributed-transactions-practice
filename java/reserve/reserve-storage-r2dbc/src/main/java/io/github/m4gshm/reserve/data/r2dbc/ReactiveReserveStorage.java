package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.reserve.data.model.Reserve;
import io.github.m4gshm.storage.ReactiveCrudStorage;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface ReactiveReserveStorage extends ReactiveCrudStorage<Reserve, String> {

    Mono<List<Reserve.Item>> saveReservedItems(String reserveId, @Valid Collection<Reserve.Item> items);
}
