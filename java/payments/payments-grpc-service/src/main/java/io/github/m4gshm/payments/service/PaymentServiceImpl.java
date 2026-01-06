package io.github.m4gshm.payments.service;

import io.github.m4gshm.LogUtils;
import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.payments.data.ReactiveAccountStorage;
import io.github.m4gshm.payments.data.ReactivePaymentStorage;
import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.postgres.prepared.transaction.ReactivePreparedTransactionService;
import io.github.m4gshm.reactive.GrpcReactive;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import payment.v1.PaymentServiceOuterClass.PaymentApproveRequest;
import payment.v1.PaymentServiceOuterClass.PaymentApproveResponse;
import payment.v1.PaymentServiceOuterClass.PaymentCancelRequest;
import payment.v1.PaymentServiceOuterClass.PaymentCancelResponse;
import payment.v1.PaymentServiceOuterClass.PaymentCreateRequest;
import payment.v1.PaymentServiceOuterClass.PaymentCreateResponse;
import payment.v1.PaymentServiceOuterClass.PaymentGetRequest;
import payment.v1.PaymentServiceOuterClass.PaymentGetResponse;
import payment.v1.PaymentServiceOuterClass.PaymentListRequest;
import payment.v1.PaymentServiceOuterClass.PaymentListResponse;
import payment.v1.PaymentServiceOuterClass.PaymentPayRequest;
import payment.v1.PaymentServiceOuterClass.PaymentPayResponse;
import payments.data.access.jooq.enums.PaymentStatus;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static io.github.m4gshm.ReactiveExceptionUtils.checkStatus;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toPayment;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toPaymentProto;
import static io.github.m4gshm.payments.service.PaymentServiceUtils.toStatusProto;
import static io.github.m4gshm.protobuf.Utils.getOrNull;
import static lombok.AccessLevel.PRIVATE;
import static payment.v1.PaymentServiceGrpc.PaymentServiceImplBase;
import static payments.data.access.jooq.enums.PaymentStatus.CANCELLED;
import static payments.data.access.jooq.enums.PaymentStatus.CREATED;
import static payments.data.access.jooq.enums.PaymentStatus.HOLD;
import static payments.data.access.jooq.enums.PaymentStatus.INSUFFICIENT;
import static payments.data.access.jooq.enums.PaymentStatus.PAID;
import static reactor.core.publisher.Mono.defer;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class PaymentServiceImpl extends PaymentServiceImplBase {
    ReactiveJooq jooq;

    GrpcReactive grpc;
    ReactivePaymentStorage reactivePaymentStorage;
    ReactiveAccountStorage reactiveAccountStorage;

    ReactivePreparedTransactionService reactivePreparedTransactionService;

    private static Payment withStatus(Payment payment, PaymentStatus status) {
        return payment.toBuilder().status(status).build();
    }

    @Override
    public void approve(PaymentApproveRequest request,
                        StreamObserver<PaymentApproveResponse> responseObserver) {
        var expected = Set.of(CREATED, INSUFFICIENT);
        paymentAccount("approve",
                responseObserver,
                getOrNull(request, r -> r.hasPreparedTransactionId(), r -> r.getPreparedTransactionId()),
                request.getId(),
                expected,
                (payment, account) -> {
                    return reactiveAccountStorage.addLock(account.clientId(), payment.amount()).flatMap(lockResult -> {
                        var status = lockResult.success() ? HOLD : INSUFFICIENT;
                        return reactivePaymentStorage.save(payment.toBuilder()
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
                    return reactiveAccountStorage.unlock(account.clientId(), payment.amount())
                            .then(reactivePaymentStorage.save(withStatus(payment, CANCELLED)).map(savedPaymant -> {
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
                "create",
                responseObserver,
                () -> defer(() -> {
                    var paymentId = UUID.randomUUID().toString();
                    var payment = toPayment(paymentId, request.getBody(), CREATED);
                    var response = PaymentCreateResponse.newBuilder()
                            .setId(paymentId)
                            .build();
                    return reactivePreparedTransactionService.prepare(
                            getOrNull(request,
                                    PaymentCreateRequest::hasPreparedTransactionId,
                                    PaymentCreateRequest::getPreparedTransactionId),
                            reactivePaymentStorage.save(payment)
                                    .thenReturn(response)
                    );
                })
        );
    }

    @Override
    public void get(PaymentGetRequest request, StreamObserver<PaymentGetResponse> responseObserver) {
        grpc.subscribe(
                "get",
                responseObserver,
                () -> reactivePaymentStorage.getById(request.getId()).map(payment -> {
                    return PaymentGetResponse.newBuilder()
                            .setPayment(toPaymentProto(payment))
                            .build();
                })
        );
    }

    @Override
    public void list(PaymentListRequest request, StreamObserver<PaymentListResponse> responseObserver) {
        grpc.subscribe(
                "list",
                responseObserver,
                () -> reactivePaymentStorage.findAll().map(payments -> {
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
                getOrNull(request,
                        PaymentPayRequest::hasPreparedTransactionId,
                        PaymentPayRequest::getPreparedTransactionId),
                request.getId(),
                Set.of(HOLD),
                (payment, account) -> {
                    return reactiveAccountStorage.writeOff(account.clientId(), payment.amount())
                            .flatMap(writeOffResult -> {
                                return reactivePaymentStorage.save(withStatus(payment, PAID)).map(_ -> {
                                    return PaymentPayResponse.newBuilder()
                                            .setId(payment.id())
                                            .setBalance(writeOffResult.balance())
                                            .build();
                                });
                            });
                });
    }

    private <T> void paymentAccount(String opName,
                                    StreamObserver<T> responseObserver,
                                    String preparedTransactionId,
                                    String paymentId,
                                    Set<PaymentStatus> expected,
                                    BiFunction<Payment, Account, Mono<T>> routine
    ) {
        grpc.subscribe("paymentAccount",
                responseObserver,
                () -> log(opName,
                        reactivePreparedTransactionService.prepare(
                                preparedTransactionId,
                                reactivePaymentStorage.getById(paymentId).flatMap(payment -> {
                                    return checkStatus(opName, "payment", paymentId, payment.status(), expected, null)
                                            .then(defer(
                                                    () -> {
                                                        return reactiveAccountStorage.getById(payment.clientId())
                                                                .flatMap(account -> routine.apply(payment, account));
                                                    }));
                                })
                        ).doOnSuccess(t -> {
                            log.debug("{}, paymentId [{}]", opName, paymentId);
                        })));
    }

}
