package payments.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import payments.data.PaymentStorage;
import payments.data.model.Payment;
import payments.v1.Payments.CancelPaymentRequest;
import payments.v1.Payments.CancelPaymentResponse;
import payments.v1.Payments.NewPaymentRequest;
import payments.v1.Payments.NewPaymentResponse;
import payments.v1.Payments.ProcessPaymentRequest;
import payments.v1.Payments.ProcessPaymentResponse;
import payments.v1.PaymentsServiceGrpc;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static reactive.GrpcUtils.subscribe;

@RequiredArgsConstructor
public class PaymentsServiceImpl extends PaymentsServiceGrpc.PaymentsServiceImplBase {
    private final PaymentStorage paymentStorage;

    @Override
    public void create(NewPaymentRequest request, StreamObserver<NewPaymentResponse> responseObserver) {
        subscribe(responseObserver, Mono.defer(() -> {
            var id = UUID.randomUUID().toString();
            var payment = request.getBody();
            var saved = paymentStorage.save(Payment.builder()
                    .id(id)
                    .externalRef(payment.getExternalRef())
                    .amount(payment.getAmount())
                    .status(Payment.Status.CREATED)
                    .build(), request.getTwoPhaseCommit());
            return saved.thenReturn(NewPaymentResponse.newBuilder().setId(id).build());
        }));
    }

    @Override
    public void cancel(CancelPaymentRequest request, StreamObserver<CancelPaymentResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void process(ProcessPaymentRequest request, StreamObserver<ProcessPaymentResponse> responseObserver) {
        super.process(request, responseObserver);
    }
}
