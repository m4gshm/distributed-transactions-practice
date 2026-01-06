package io.github.m4gshm.payments.data;

import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.storage.CrudStorage;

public interface PaymentStorage extends CrudStorage<Payment, String> {
}
