package io.github.m4gshm.payments.service;

import io.github.m4gshm.payments.data.model.Payment;
import lombok.experimental.UtilityClass;
import payment.v1.PaymentOuterClass;
import payment.v1.PaymentServiceOuterClass.PaymentCreateRequest.PaymentCreate;
import payments.data.access.jooq.enums.PaymentStatus;

@UtilityClass
public class PaymentServiceUtils {
    static PaymentOuterClass.Payment toPaymentProto(Payment payment) {
        return PaymentOuterClass.Payment.newBuilder()
                .setClientId(payment.clientId())
                .setAmount(payment.amount())
                .setExternalRef(payment.externalRef())
                .setStatus(toStatusProto(payment.status()))
                .build();
    }

    public static PaymentOuterClass.Payment.Status toStatusProto(PaymentStatus status) {
        return switch (status) {
            case CREATED -> PaymentOuterClass.Payment.Status.CREATED;
            case HOLD -> PaymentOuterClass.Payment.Status.HOLD;
            case INSUFFICIENT -> PaymentOuterClass.Payment.Status.INSUFFICIENT;
            case PAID -> PaymentOuterClass.Payment.Status.PAID;
            case CANCELLED -> PaymentOuterClass.Payment.Status.CANCELLED;
        };
    }

    static Payment toPayment(String id, PaymentOuterClass.Payment payment, PaymentStatus status) {
        return Payment.builder()
                .id(id)
                .externalRef(payment.getExternalRef())
                .clientId(payment.getClientId())
                .amount(payment.getAmount())
                .status(status)
                .build();
    }

    static Payment toPayment(String id, PaymentCreate payment, PaymentStatus status) {
        return Payment.builder()
                .id(id)
                .externalRef(payment.getExternalRef())
                .clientId(payment.getClientId())
                .amount(payment.getAmount())
                .status(status)
                .build();
    }

}
