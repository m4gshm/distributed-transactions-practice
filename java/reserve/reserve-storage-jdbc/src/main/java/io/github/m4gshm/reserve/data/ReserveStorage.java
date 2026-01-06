package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.Reserve;
import io.github.m4gshm.storage.CrudStorage;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;

public interface ReserveStorage extends CrudStorage<Reserve, String> {

    List<Reserve.Item> saveReservedItems(String reserveId, @Valid Collection<Reserve.Item> items);
}
