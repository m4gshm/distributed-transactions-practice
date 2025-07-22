package orders.data.storage;

import io.github.m4gshm.storage.CrudStorage;
import orders.data.model.Order;

public interface OrderStorage extends CrudStorage<Order, String> {

}
