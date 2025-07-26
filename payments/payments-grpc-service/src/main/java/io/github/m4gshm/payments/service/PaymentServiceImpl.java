package io.github.m4gshm.payments.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass;
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
import payment.v1.PaymentOuterClass.PaymentProcessRequest;
import payment.v1.PaymentOuterClass.PaymentProcessResponse;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.payments.data.PaymentStorage;
import io.github.m4gshm.payments.data.model.Payment;

import java.util.UUID;

import static io.github.m4gshm.reactive.GrpcUtils.subscribe;
import static java.lang.Boolean.TRUE;
import static payment.v1.PaymentServiceGrpc.PaymentServiceImplBase;
import static reactor.core.publisher.Mono.defer;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends PaymentServiceImplBase {
    private final PaymentStorage paymentStorage;
    private final AccountStorage accountStorage;

    private static PaymentOuterClass.Payment toProto(Payment p) {
        return PaymentOuterClass.Payment.newBuilder()
                .setClientId(p.clientId())
                .setAmount(p.amount())
                .setExternalRef(p.externalRef())
                .build();
    }

    private static Payment toDataModel(String id, PaymentOuterClass.Payment payment, Payment.Status status) {
        return Payment.builder()
                .id(id)
                .externalRef(payment.getExternalRef())
                .clientId(payment.getClientId())
                .amount(payment.getAmount())
                .status(status)
                .build();
    }

    @Override
    public void create(PaymentCreateRequest request, StreamObserver<PaymentCreateResponse> responseObserver) {
        subscribe(responseObserver, defer(() -> {
            var id = UUID.randomUUID().toString();
            var payment = request.getBody();
            var saved = paymentStorage.save(toDataModel(id, payment, Payment.Status.CREATED), request.getTwoPhaseCommit());
            return saved.thenReturn(PaymentCreateResponse.newBuilder().setId(id).build());
        }));
    }

    @Override
    public void approve(PaymentApproveRequest request, StreamObserver<PaymentApproveResponse> responseObserver) {
        subscribe(responseObserver, paymentStorage.getById(request.getId()).flatMap(payment -> {
            return accountStorage.getById(payment.clientId()).flatMap(account -> {
                return accountStorage.addLock(account, payment.amount(), payment.id(), request.getTwoPhaseCommit())
                        .map(success -> {
                            return TRUE.equals(success)
                                    ? PaymentApproveResponse.Status.APPROVED
                                    : PaymentApproveResponse.Status.UNCEFFICIENT_FUNDS;
                        }).map(status -> {
                            return PaymentApproveResponse.newBuilder().setId(request.getId()).setStatus(status).build();
                        });
            });
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
        subscribe(responseObserver, paymentStorage.getById(request.getId()).map(payment -> {
            return PaymentGetResponse.newBuilder().setPayment(toProto(payment)).build();
        }));
    }

    @Override
    public void list(PaymentListRequest request, StreamObserver<PaymentListResponse> responseObserver) {
        subscribe(responseObserver, paymentStorage.findAll().map(payments -> {
            return PaymentListResponse.newBuilder()
                    .addAllPayments(payments.stream().map(PaymentServiceImpl::toProto).toList())
                    .build();
        }));
    }
}
