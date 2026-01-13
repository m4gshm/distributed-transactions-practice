package io.github.m4gshm.reserve.data.r2dbc;

import io.github.m4gshm.reserve.data.model.Reserve;
import io.github.m4gshm.storage.ReactiveCrudStorage;

public interface ReactiveReserveStorage extends ReactiveCrudStorage<Reserve, String> {

}
