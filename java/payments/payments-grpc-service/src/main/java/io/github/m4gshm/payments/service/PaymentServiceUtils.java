package io.github.m4gshm.payments.service;

import io.github.m4gshm.payments.data.model.Payment;
import lombok.experimental.UtilityClass;
import payment.v1.PaymentApi.PaymentCreateRequest;
import payment.v1.PaymentModel;

@UtilityClass
public class PaymentServiceUtils {
    static PaymentModel.Payment toPaymentProto(Payment payment) {
        return PaymentModel.Payment.newBuilder()
                .setClientId(payment.clientId())
                .setAmount(payment.amount())
                .setExternalRef(payment.externalRef())
                .setStatus(toStatusProto(payment.status()))
                .build();
    }

    public static PaymentModel.Payment.Status toStatusProto(Payment.Status status) {
        return switch (status) {
            case CREATED -> PaymentModel.Payment.Status.CREATED;
            case HOLD -> PaymentModel.Payment.Status.HOLD;
            case INSUFFICIENT -> PaymentModel.Payment.Status.INSUFFICIENT;
            case PAID -> PaymentModel.Payment.Status.PAID;
            case CANCELLED -> PaymentModel.Payment.Status.CANCELLED;
        };
    }

    static Payment toPayment(String id, PaymentModel.Payment payment, Payment.Status status) {
        return Payment.builder()
                .id(id)
                .externalRef(payment.getExternalRef())
                .clientId(payment.getClientId())
                .amount(payment.getAmount())
                .status(status)
                .build();
    }

    static Payment toPayment(String id, PaymentCreateRequest.PaymentCreate payment, Payment.Status status) {
        return Payment.builder()
                .id(id)
                .externalRef(payment.getExternalRef())
                .clientId(payment.getClientId())
                .amount(payment.getAmount())
                .status(status)
                .build();
    }

}
