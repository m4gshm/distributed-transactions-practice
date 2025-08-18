package io.github.m4gshm.payments.service;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.payments.data.model.Payment.Status.CANCELLED;
import static io.github.m4gshm.payments.data.model.Payment.Status.CREATED;
import static io.github.m4gshm.payments.data.model.Payment.Status.HOLD;
import static io.github.m4gshm.payments.data.model.Payment.Status.INSUFFICIENT;
import static io.github.m4gshm.payments.data.model.Payment.Status.PAID;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toDataModel;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toProto;
import static lombok.AccessLevel.PRIVATE;
import static payment.v1.PaymentOuterClass.PaymentApproveResponse.Status.APPROVED;
import static payment.v1.PaymentOuterClass.PaymentApproveResponse.Status.INSUFFICIENT_AMOUNT;
import static payment.v1.PaymentServiceGrpc.PaymentServiceImplBase;
import static reactor.core.publisher.Mono.defer;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import org.springframework.stereotype.Service;

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
        var preparedTransactionId = request.getPreparedTransactionId();
        var expected = Set.of(CREATED, INSUFFICIENT);
        paymentAccount(responseObserver,
                preparedTransactionId,
                request.getId(),
                expected,
                (payment, account) -> {
                    return accountStorage.addLock(account.clientId(), payment.amount()).flatMap(lockResult -> {
                        var status = lockResult.success() ? HOLD : INSUFFICIENT;
                        return paymentStorage.save(payment.toBuilder()
                                .status(status)
                                .insufficient(status
                                        == INSUFFICIENT
                                                ? lockResult.insufficientAmount()
                                                : null)
                                .build())
                                .map(_ -> PaymentApproveResponse.newBuilder()
                                        .setId(request.getId())
                                        .setStatus(lockResult.success()
                                                ? APPROVED
                                                : INSUFFICIENT_AMOUNT)
                                        .setInsufficientAmount(lockResult.insufficientAmount())
                                        .build());
                    });
                });
    }

    @Override
    public void cancel(PaymentCancelRequest request, StreamObserver<PaymentCancelResponse> responseObserver) {
        paymentAccount(responseObserver,
                request.getPreparedTransactionId(),
                request.getId(),
                Set.of(CREATED, INSUFFICIENT, HOLD),
                (payment,
                 account) -> {
                    return accountStorage.unlock(account.clientId(), payment.amount())
                            .then(paymentStorage.save(withStatus(payment, CANCELLED)).map(_ -> {
                                return PaymentCancelResponse.newBuilder()
                                        .setId(payment.id())
                                        .build();
                            }));
                });
    }

    @Override
    public void create(PaymentCreateRequest request, StreamObserver<PaymentCreateResponse> responseObserver) {
        grpc.subscribe(responseObserver, defer(() -> {
            var paymentId = UUID.randomUUID().toString();
            var payment = toDataModel(paymentId, request.getBody(), CREATED);
            var response = PaymentCreateResponse.newBuilder()
                    .setId(paymentId)
                    .build();
            return jooq.inTransaction(dsl -> prepare(dsl,
                    request.getPreparedTransactionId(),
                    paymentStorage.save(payment)
                            .thenReturn(response)));
        }));
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

    @Override
    public void pay(PaymentPayRequest request, StreamObserver<PaymentPayResponse> responseObserver) {
        paymentAccount(responseObserver,
                request.getPreparedTransactionId(),
                request.getId(),
                Set.of(HOLD),
                (payment,
                 account) -> {
                    return accountStorage.writeOff(account.clientId(), payment.amount())
                            .flatMap(writeOffResult -> {
                                return paymentStorage.save(withStatus(payment, PAID)).map(_ -> {
                                    return PaymentPayResponse.newBuilder()
                                            .setId(payment.id())
                                            .setBalance(writeOffResult.balance())
                                            .build();
                                });
                            });
                });
    }

    private <T> void paymentAccount(StreamObserver<T> responseObserver,
                                    String preparedTransactionId,
                                    String paymentId,
                                    Set<Payment.Status> expected,
                                    BiFunction<Payment, Account, Mono<T>> routine) {
        grpc.subscribe(responseObserver, jooq.inTransaction(dsl -> {
            return prepare(dsl,
                    preparedTransactionId,
                    paymentStorage.getById(paymentId).flatMap(payment -> {
                        return checkStatus(payment.status(), expected).then(defer(() -> {
                            return accountStorage.getById(payment.clientId())
                                    .flatMap(account -> routine.apply(payment, account));
                        }));
                    }));
        }));
    }
}
