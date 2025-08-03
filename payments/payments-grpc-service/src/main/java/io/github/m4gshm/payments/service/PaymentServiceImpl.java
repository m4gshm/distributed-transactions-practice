package io.github.m4gshm.payments.service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass.*;

import java.util.UUID;

import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.payments.data.model.Payment.Status.created;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toDataModel;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toProto;
import static lombok.AccessLevel.PRIVATE;
import static payment.v1.PaymentOuterClass.PaymentApproveResponse.Status.APPROVED;
import static payment.v1.PaymentOuterClass.PaymentApproveResponse.Status.INSUFFICIENT_AMOUNT;
import static payment.v1.PaymentServiceGrpc.PaymentServiceImplBase;
import static reactor.core.publisher.Mono.defer;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class PaymentServiceImpl extends PaymentServiceImplBase {
    Jooq jooq;
    GrpcReactive grpc;
    PaymentStorage paymentStorage;
    AccountStorage accountStorage;

    @Override
    public void create(PaymentCreateRequest request, StreamObserver<PaymentCreateResponse> responseObserver) {
        grpc.subscribe(responseObserver, defer(() -> {
            var paymentId = UUID.randomUUID().toString();
            var payment = toDataModel(paymentId, request.getBody(), created);
            var response = PaymentCreateResponse.newBuilder()
                    .setId(paymentId)
                    .build();
            return jooq.transactional(dsl -> prepare(request.getTwoPhaseCommit(), dsl, paymentId,
                    paymentStorage.save(payment).thenReturn(response)
            ));
        }));
    }

    @Override
    public void approve(PaymentApproveRequest request, StreamObserver<PaymentApproveResponse> responseObserver) {
        grpc.subscribe(responseObserver, jooq.transactional(dsl -> {
            var paymentId = request.getId();
            return prepare(request.getTwoPhaseCommit(), dsl, paymentId, paymentStorage.getById(paymentId
            ).flatMap(payment -> accountStorage.getById(payment.clientId()).flatMap(account -> {
                return accountStorage.addLock(account, payment.amount()).flatMap(lockResult -> {
                    return paymentStorage.save(payment.toBuilder()
                            .status(lockResult.success()
                                    ? Payment.Status.hold
                                    : Payment.Status.insufficient)
                            .build()).map(_ -> PaymentApproveResponse.newBuilder()
                            .setId(paymentId)
                            .setStatus(lockResult.success() ? APPROVED : INSUFFICIENT_AMOUNT)
                            .setInsufficientAmount(lockResult.insufficientAmount())
                            .build());
                });
            })));
        }));
    }

    @Override
    public void cancel(PaymentCancelRequest request, StreamObserver<PaymentCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void process(PaymentProcessRequest request, StreamObserver<PaymentProcessResponse> responseObserver) {
        super.process(request, responseObserver);
    }

    @Override
    public void get(PaymentGetRequest request, StreamObserver<PaymentGetResponse> responseObserver) {
        grpc.subscribe(responseObserver, paymentStorage.getById(request.getId()).map(payment -> {
            return PaymentGetResponse.newBuilder()
                    .setPayment(toProto(payment))
                    .build();
        }));
    }

    @Override
    public void list(PaymentListRequest request, StreamObserver<PaymentListResponse> responseObserver) {
        grpc.subscribe(responseObserver, paymentStorage.findAll().map(payments -> {
            return PaymentListResponse.newBuilder()
                    .addAllPayments(payments.stream().map(PaymentServiceUtils::toProto).toList())
                    .build();
        }));
    }
}
