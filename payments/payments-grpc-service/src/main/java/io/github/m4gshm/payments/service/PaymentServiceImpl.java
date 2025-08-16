package io.github.m4gshm.payments.service;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass.*;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
import static io.github.m4gshm.jooq.utils.TwoPhaseTransaction.prepare;
import static io.github.m4gshm.payments.data.model.Payment.Status.*;
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

    private static Payment withStatus(Payment payment, Payment.Status status) {
        return payment.toBuilder().status(status).build();
    }

    @Override
    public void create(PaymentCreateRequest request, StreamObserver<PaymentCreateResponse> responseObserver) {
        grpc.subscribe(responseObserver, defer(() -> {
            var paymentId = UUID.randomUUID().toString();
            var payment = toDataModel(paymentId, request.getBody(), created);
            var response = PaymentCreateResponse.newBuilder()
                                                .setId(paymentId)
                                                .build();
            return jooq.transactional(dsl -> prepare(request.getTwoPhaseCommit(),
                                                     dsl,
                                                     paymentId,
                                                     paymentStorage.save(payment)
                                                                   .thenReturn(response)));
        }));
    }

    @Override
    public void approve(PaymentApproveRequest request, StreamObserver<PaymentApproveResponse> responseObserver) {
        var twoPhaseCommit = request.getTwoPhaseCommit();
        var expected = Set.of(created, insufficient);
        paymentAccount(responseObserver,
                       twoPhaseCommit,
                       request.getId(),
                       expected,
                       (payment,
                        account) -> {
                           return accountStorage.addLock(account.clientId(), payment.amount()).flatMap(lockResult -> {
                               var status = lockResult.success() ? hold : insufficient;
                               return paymentStorage.save(payment.toBuilder()
                                                                 .status(status)
                                                                 .insufficient(status
                                                                               == insufficient ? lockResult.insufficientAmount()
                                                                                               : null)
                                                                 .build())
                                                    .map(_ -> PaymentApproveResponse.newBuilder()
                                                                                    .setId(request.getId())
                                                                                    .setStatus(lockResult.success() ? APPROVED
                                                                                                                    : INSUFFICIENT_AMOUNT)
                                                                                    .setInsufficientAmount(lockResult.insufficientAmount())
                                                                                    .build());
                           });
                       });
    }

    @Override
    public void pay(PaymentPayRequest request, StreamObserver<PaymentPayResponse> responseObserver) {
        paymentAccount(responseObserver,
                       request.getTwoPhaseCommit(),
                       request.getId(),
                       Set.of(hold),
                       (payment,
                        account) -> {
                           return accountStorage.writeOff(account.clientId(), payment.amount())
                                                .flatMap(writeOffResult -> {
                                                    return paymentStorage.save(withStatus(payment, paid)).map(_ -> {
                                                        return PaymentPayResponse.newBuilder()
                                                                                 .setId(payment.id())
                                                                                 .setBalance(writeOffResult.balance())
                                                                                 .build();
                                                    });
                                                });
                       });
    }

    @Override
    public void cancel(PaymentCancelRequest request, StreamObserver<PaymentCancelResponse> responseObserver) {
        paymentAccount(responseObserver,
                       request.getTwoPhaseCommit(),
                       request.getId(),
                       Set.of(created, insufficient, hold),
                       (payment,
                        account) -> {
                           return accountStorage.unlock(account.clientId(), payment.amount())
                                                .then(paymentStorage.save(withStatus(payment, cancelled)).map(_ -> {
                                                    return PaymentCancelResponse.newBuilder()
                                                                                .setId(payment.id())
                                                                                .build();
                                                }));
                       });
    }

    private <T> void paymentAccount(StreamObserver<T> responseObserver,
                                    boolean twoPhaseCommit,
                                    String paymentId,
                                    Set<Payment.Status> expected,
                                    BiFunction<Payment, Account, Mono<T>> routine) {
        grpc.subscribe(responseObserver, jooq.transactional(dsl -> {
            return prepare(twoPhaseCommit, dsl, paymentId, paymentStorage.getById(paymentId).flatMap(payment -> {
                return checkStatus(payment.status(), expected).then(defer(() -> {
                    return accountStorage.getById(payment.clientId())
                                         .flatMap(account -> routine.apply(payment, account));
                }));
            }));
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
}
