package io.github.m4gshm.payments.data;

import io.github.m4gshm.storage.CrudStorage;
import io.github.m4gshm.payments.data.model.Payment;

public interface PaymentStorage extends CrudStorage<Payment, String> {
}
