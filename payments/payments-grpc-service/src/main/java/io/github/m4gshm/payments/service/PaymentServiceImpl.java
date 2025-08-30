package io.github.m4gshm.payments.service;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.payments.data.model.Payment.Status.CANCELLED;
import static io.github.m4gshm.payments.data.model.Payment.Status.CREATED;
import static io.github.m4gshm.payments.data.model.Payment.Status.HOLD;
import static io.github.m4gshm.payments.data.model.Payment.Status.INSUFFICIENT;
import static io.github.m4gshm.payments.data.model.Payment.Status.PAID;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toPayment;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toPaymentProto;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toStatusProto;
import static io.github.m4gshm.postgres.prepared.transaction.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.protobuf.Utils.getOrNull;
import static lombok.AccessLevel.PRIVATE;
import static payment.v1.PaymentServiceGrpc.PaymentServiceImplBase;
import static reactor.core.publisher.Mono.defer;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import org.springframework.stereotype.Service;

import io.github.m4gshm.LogUtils;
import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import payment.v1.PaymentOuterClass.PaymentApproveRequest;
import payment.v1.PaymentOuterClass.PaymentApproveResponse;
import payment.v1.PaymentOuterClass.PaymentCancelRequest;
import payment.v1.PaymentOuterClass.PaymentCancelResponse;
import payment.v1.PaymentOuterClass.PaymentCreateRequest;
import payment.v1.PaymentOuterClass.PaymentCreateResponse;
import payment.v1.PaymentOuterClass.PaymentGetRequest;
import payment.v1.PaymentOuterClass.PaymentGetResponse;
import payment.v1.PaymentOuterClass.PaymentListRequest;
import payment.v1.PaymentOuterClass.PaymentListResponse;
import payment.v1.PaymentOuterClass.PaymentPayRequest;
import payment.v1.PaymentOuterClass.PaymentPayResponse;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class PaymentServiceImpl extends PaymentServiceImplBase {
    Jooq jooq;

    GrpcReactive grpc;
    PaymentStorage paymentStorage;
    AccountStorage accountStorage;

    private static Payment withStatus(Payment payment, Payment.Status status) {
        return payment.toBuilder().status(status).build();
    }

    @Override
    public void approve(PaymentApproveRequest request, StreamObserver<PaymentApproveResponse> responseObserver) {
        var expected = Set.of(CREATED, INSUFFICIENT);
        paymentAccount("approve",
                responseObserver,
                getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                request.getId(),
                expected,
                (payment, account) -> {
                    return accountStorage.addLock(account.clientId(), payment.amount()).flatMap(lockResult -> {
                        var status = lockResult.success() ? HOLD : INSUFFICIENT;
                        return paymentStorage.save(payment.toBuilder()
                                .status(status)
                                .insufficient(status == INSUFFICIENT ? lockResult.insufficientAmount() : null)
                                .build())
                                .map(_ -> PaymentApproveResponse.newBuilder()
                                        .setId(request.getId())
                                        .setStatus(toStatusProto(status))
                                        .setInsufficientAmount(lockResult.insufficientAmount())
                                        .build());
                    });
                }
        );
    }

    @Override
    public void cancel(PaymentCancelRequest request, StreamObserver<PaymentCancelResponse> responseObserver) {
        paymentAccount("cancel",
                responseObserver,
                getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                request.getId(),
                Set.of(CREATED, INSUFFICIENT, HOLD),
                (payment, account) -> {
                    return accountStorage.unlock(account.clientId(), payment.amount())
                            .then(paymentStorage.save(withStatus(payment, CANCELLED)).map(savedPaymant -> {
                                return PaymentCancelResponse.newBuilder()
                                        .setId(savedPaymant.id())
                                        .setStatus(toStatusProto(savedPaymant.status()))
                                        .build();
                            }));
                }
        );
    }

    @Override
    public void create(PaymentCreateRequest request, StreamObserver<PaymentCreateResponse> responseObserver) {
        grpc.subscribe(
                responseObserver,
                defer(() -> {
                    var paymentId = UUID.randomUUID().toString();
                    var payment = toPayment(paymentId, request.getBody(), CREATED);
                    var response = PaymentCreateResponse.newBuilder()
                            .setId(paymentId)
                            .build();
                    return jooq.inTransaction(dsl -> prepare(
                            dsl,
                            getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                            paymentStorage.save(payment)
                                    .thenReturn(response)
                    ));
                })
        );
    }

    @Override
    public void get(PaymentGetRequest request, StreamObserver<PaymentGetResponse> responseObserver) {
        grpc.subscribe(
                responseObserver,
                paymentStorage.getById(request.getId()).map(payment -> {
                    return PaymentGetResponse.newBuilder()
                            .setPayment(toPaymentProto(payment))
                            .build();
                })
        );
    }

    @Override
    public void list(PaymentListRequest request, StreamObserver<PaymentListResponse> responseObserver) {
        grpc.subscribe(
                responseObserver,
                paymentStorage.findAll().map(payments -> {
                    return PaymentListResponse.newBuilder()
                            .addAllPayments(payments.stream().map(PaymentServiceUtils::toPaymentProto).toList())
                            .build();
                })
        );
    }

    private <T> Mono<T> log(String category, Mono<T> mono) {
        return LogUtils.log(getClass(), category, mono);
    }

    @Override
    public void pay(PaymentPayRequest request, StreamObserver<PaymentPayResponse> responseObserver) {
        paymentAccount("pay",
                responseObserver,
                getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                request.getId(),
                Set.of(HOLD),
                (payment, account) -> {
                    return accountStorage.writeOff(account.clientId(), payment.amount())
                            .flatMap(writeOffResult -> {
                                return paymentStorage.save(withStatus(payment, PAID)).map(_ -> {
                                    return PaymentPayResponse.newBuilder()
                                            .setId(payment.id())
                                            .setBalance(writeOffResult.balance())
                                            .build();
                                });
                            });
                }
        );
    }

    private <T> void paymentAccount(String opName,
                                    StreamObserver<T> responseObserver,
                                    String preparedTransactionId,
                                    String paymentId,
                                    Set<Payment.Status> expected,
                                    BiFunction<Payment, Account, Mono<T>> routine
    ) {
        grpc.subscribe(
                responseObserver,
                log(opName, jooq.inTransaction(dsl -> {
                    return prepare(
                            dsl,
                            preparedTransactionId,
                            paymentStorage.getById(paymentId).flatMap(payment -> {
                                return checkStatus(opName, payment.status(), expected, null).then(defer(() -> {
                                    return accountStorage.getById(payment.clientId())
                                            .flatMap(account -> routine.apply(payment, account));
                                }));
                            })
                    );
                })
                ));
    }
}
