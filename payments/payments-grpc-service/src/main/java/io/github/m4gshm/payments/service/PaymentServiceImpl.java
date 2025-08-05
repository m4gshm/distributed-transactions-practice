package io.github.m4gshm.payments.service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Payment.Status;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass.*;

import java.util.Set;
import java.util.UUID;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.payments.data.model.Payment.Status.*;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toDataModel;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toProto;
import static lombok.AccessLevel.PRIVATE;
import static payment.v1.PaymentOuterClass.PaymentApproveResponse.Status.APPROVED;
import static payment.v1.PaymentOuterClass.PaymentApproveResponse.Status.INSUFFICIENT_AMOUNT;
import static payment.v1.PaymentServiceGrpc.PaymentServiceImplBase;
import static reactor.core.publisher.Mono.*;

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
            ).flatMap(payment -> {
                return checkStatus(payment.status(), Set.of(created, insufficient), just(payment)).then(accountStorage.getById(payment.clientId()
                ).flatMap(account -> {
                    return accountStorage.addLock(account, payment.amount()).flatMap(lockResult -> {
                        var status = lockResult.success()
                                ? hold
                                : insufficient;
                        return paymentStorage.save(payment.toBuilder()
                                .status(status)
                                .insufficient(status == insufficient ? lockResult.insufficientAmount() : null)
                                .build()
                        ).map(_ -> {
                            return PaymentApproveResponse.newBuilder()
                                    .setId(paymentId)
                                    .setStatus(lockResult.success() ? APPROVED : INSUFFICIENT_AMOUNT)
                                    .setInsufficientAmount(lockResult.insufficientAmount())
                                    .build();
                        });
                    });
                }));
            }));
        }));
    }

    @Override
    public void pay(PaymentPayRequest request, StreamObserver<PaymentPayResponse> responseObserver) {
        grpc.subscribe(responseObserver, jooq.transactional(dsl -> {
            var paymentId = request.getId();
            var twoPhaseCommit = request.getTwoPhaseCommit();
            return prepare(twoPhaseCommit, dsl, paymentId, paymentStorage.getById(paymentId).flatMap(payment -> {
                return checkStatus(
                        payment.status(), Set.of(hold), accountStorage.getById(payment.clientId())
                ).flatMap(account -> {
                    return accountStorage.writeOff(account, payment.amount()).flatMap(writeOffResult -> {
                        var success = writeOffResult.success();
                        if (success) {
                            return paymentStorage.save(payment.toBuilder().status(paid).build()).map(_ -> {
                                return PaymentPayResponse.newBuilder()
                                        .setId(paymentId)
                                        .setBalance(writeOffResult.balance())
                                        .build();
                            });
                        } else {
                            return error(new WriteOffException(
                                    writeOffResult.insufficientAmount(),
                                    writeOffResult.insufficientHold())
                            );
                        }
                    });
                });
            }));
        }));
    }

    @Override
    public void cancel(PaymentCancelRequest request, StreamObserver<PaymentCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
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
