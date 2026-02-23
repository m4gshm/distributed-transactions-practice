package io.github.m4gshm.payments.data;

import io.github.m4gshm.storage.ReactiveCrudStorage;
import io.github.m4gshm.payments.data.model.Payment;

public interface ReactivePaymentStorage extends ReactiveCrudStorage<Payment, String> {
}
