package payments.data;

import io.github.m4gshm.storage.CrudStorage;
import payments.data.model.Payment;

public interface PaymentStorage extends CrudStorage<Payment, String> {
}
