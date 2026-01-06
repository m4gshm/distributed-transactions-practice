package io.github.m4gshm.reserve.data;

import io.github.m4gshm.reserve.data.model.ItemOp;
import io.github.m4gshm.reserve.data.model.WarehouseItem;
import io.github.m4gshm.storage.ReadOperations;

import java.util.Collection;
import java.util.List;

public interface WarehouseItemStorage extends ReadOperations<WarehouseItem, String> {

    List<ItemOp.Result> cancelReserve(Collection<ItemOp> items);

    List<ItemOp.Result> release(Collection<ItemOp> items);

    List<ItemOp.ReserveResult> reserve(Collection<ItemOp> items);

    ItemOp.Result topUp(String id, int amount);

}
