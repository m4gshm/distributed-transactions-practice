package io.github.m4gshm.reserve.data;

import io.github.m4gshm.storage.CrudStorage;
import reactor.core.publisher.Mono;
import io.github.m4gshm.reserve.data.model.Reserve;

import java.util.Collection;
import java.util.List;

public interface ReserveStorage extends CrudStorage<Reserve, String> {

    Mono<List<Reserve.Item>> saveReservedItems(String reserveId, Collection<Reserve.Item> items);
}
