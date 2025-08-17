package io.github.m4gshm.payments.service;

import io.github.m4gshm.payments.data.model.Payment;
import lombok.experimental.UtilityClass;
import payment.v1.PaymentOuterClass;

@UtilityClass
public class PaymentServiceUtils {
    static PaymentOuterClass.Payment toProto(Payment payment) {
        return PaymentOuterClass.Payment.newBuilder()
                .setClientId(payment.clientId())
                .setAmount(payment.amount())
                .setExternalRef(payment.externalRef())
                .setStatus(toProtoStatus(payment.status()))
                .build();
    }

    private static PaymentOuterClass.Payment.Status toProtoStatus(Payment.Status status) {
        return switch (status) {
            case created -> PaymentOuterClass.Payment.Status.CREATED;
            case hold -> PaymentOuterClass.Payment.Status.HOLD;
            case insufficient -> PaymentOuterClass.Payment.Status.INSUFFICIENT;
            case paid -> PaymentOuterClass.Payment.Status.PAID;
            case cancelled -> PaymentOuterClass.Payment.Status.CANCELLED;
        };
    }

    static Payment toDataModel(String id, PaymentOuterClass.Payment payment, Payment.Status status) {
        return Payment.builder()
                .id(id)
                .externalRef(payment.getExternalRef())
                .clientId(payment.getClientId())
                .amount(payment.getAmount())
                .status(status)
                .build();
    }

    static Payment toDataModel(String id,
                               PaymentOuterClass.PaymentCreateRequest.PaymentCreate payment,
                               Payment.Status status) {
        return Payment.builder()
                .id(id)
                .externalRef(payment.getExternalRef())
                .clientId(payment.getClientId())
                .amount(payment.getAmount())
                .status(status)
                .build();
    }

}
