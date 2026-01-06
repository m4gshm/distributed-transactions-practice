package io.github.m4gshm.payments.service;

import io.github.m4gshm.Grpc;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Account;
import io.github.m4gshm.payments.data.model.Payment;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.protobuf.Utils;
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

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static io.github.m4gshm.ExceptionUtils.checkStatus;
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

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class PaymentServiceImpl extends PaymentServiceImplBase {
    Grpc grpc;
    PaymentStorage paymentStorage;
    AccountStorage accountStorage;

    PreparedTransactionService preparedTransactionService;

    private static Payment withStatus(Payment payment, PaymentStatus status) {
        return payment.toBuilder().status(status).build();
    }

    @Override
    public void approve(PaymentApproveRequest request,
                        StreamObserver<PaymentApproveResponse> responseObserver) {
        paymentAccount("approve",
                responseObserver,
                getOrNull(request,
                        PaymentApproveRequest::hasPreparedTransactionId,
                        PaymentApproveRequest::getPreparedTransactionId),
                request.getId(),
                Set.of(CREATED, INSUFFICIENT),
                (payment, account) -> {
                    var lockResult = accountStorage.addLock(account.clientId(), payment.amount());
                    var status = lockResult.success() ? HOLD : INSUFFICIENT;
                    paymentStorage.save(payment.toBuilder()
                            .status(status)
                            .insufficient(status == INSUFFICIENT ? lockResult.insufficientAmount() : null)
                            .build());
                    return PaymentApproveResponse.newBuilder()
                            .setId(request.getId())
                            .setStatus(toStatusProto(status))
                            .setInsufficientAmount(lockResult.insufficientAmount())
                            .build();
                }
        );
    }

    @Override
    public void cancel(PaymentCancelRequest request, StreamObserver<PaymentCancelResponse> responseObserver) {
        paymentAccount("cancel",
                responseObserver,
                getOrNull(request,
                        PaymentCancelRequest::hasPreparedTransactionId,
                        PaymentCancelRequest::getPreparedTransactionId),
                request.getId(),
                Set.of(CREATED, INSUFFICIENT, HOLD),
                (payment, account) -> {
                    accountStorage.unlock(account.clientId(), payment.amount());
                    var savedPayment = paymentStorage.save(withStatus(payment, CANCELLED));
                    return PaymentCancelResponse.newBuilder()
                            .setId(savedPayment.id())
                            .setStatus(toStatusProto(savedPayment.status()))
                            .build();
                });
    }

    @Override
    public void create(PaymentCreateRequest request, StreamObserver<PaymentCreateResponse> responseObserver) {
        grpc.subscribe("create", responseObserver, () -> {
            var paymentId = UUID.randomUUID().toString();
            var payment = toPayment(paymentId, request.getBody(), CREATED);
            var response = PaymentCreateResponse.newBuilder()
                    .setId(paymentId)
                    .build();
            var preparedTransactionId = getOrNull(request,
                    PaymentCreateRequest::hasPreparedTransactionId,
                    PaymentCreateRequest::getPreparedTransactionId);
            if (preparedTransactionId != null) {
                preparedTransactionService.prepare(preparedTransactionId);
            }
            paymentStorage.save(payment);
            return response;
        });
    }

    @Override
    public void get(PaymentGetRequest request, StreamObserver<PaymentGetResponse> responseObserver) {
        grpc.subscribe("get", responseObserver, () -> {
            var payment = paymentStorage.getById(request.getId());
            return PaymentGetResponse.newBuilder()
                    .setPayment(toPaymentProto(payment))
                    .build();
        });
    }

    @Override
    public void list(PaymentListRequest request, StreamObserver<PaymentListResponse> responseObserver) {
        grpc.subscribe("list", responseObserver, () -> {
            var payments = paymentStorage.findAll();
            return PaymentListResponse.newBuilder()
                    .addAllPayments(payments.stream().map(PaymentServiceUtils::toPaymentProto).toList())
                    .build();
        });
    }

    @Override
    public void pay(PaymentPayRequest request, StreamObserver<PaymentPayResponse> responseObserver) {
        paymentAccount("pay",
                responseObserver,
                Utils.getOrNull(request,
                        PaymentPayRequest::hasPreparedTransactionId,
                        PaymentPayRequest::getPreparedTransactionId
                ),
                request.getId(),
                Set.of(HOLD),
                (payment, account) -> {
                    var writeOffResult = accountStorage.writeOff(account.clientId(), payment.amount());
                    paymentStorage.save(withStatus(payment, PAID));
                    return PaymentPayResponse.newBuilder()
                            .setId(payment.id())
                            .setBalance(writeOffResult.balance())
                            .build();
                });
    }

    private <T> void paymentAccount(String opName,
                                    StreamObserver<T> responseObserver,
                                    String preparedTransactionId,
                                    String paymentId,
                                    Set<PaymentStatus> expected,
                                    BiFunction<Payment, Account, T> routine
    ) {
        grpc.subscribe("paymentAccount", responseObserver, () -> {
            if (preparedTransactionId != null) {
                preparedTransactionService.prepare(preparedTransactionId);
            }
            var payment = paymentStorage.getById(paymentId);
            checkStatus(opName, "payment", paymentId, payment.status(), expected, null);
            return routine.apply(payment, accountStorage.getById(payment.clientId()));
        });
    }
}
