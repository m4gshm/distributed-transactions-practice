package payments.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import payments.v1.Payments;
import payments.v1.Payments.CancelPaymentRequest;
import payments.v1.Payments.CancelPaymentResponse;
import payments.v1.Payments.NewPaymentRequest;
import payments.v1.Payments.NewPaymentResponse;
import payments.v1.Payments.ProcessPaymentRequest;
import payments.v1.Payments.ProcessPaymentResponse;
import payments.v1.PaymentsServiceGrpc;

import java.util.UUID;

@RequiredArgsConstructor
public class PaymentsServiceImpl extends PaymentsServiceGrpc.PaymentsServiceImplBase {
    @Override
    public void create(NewPaymentRequest request, StreamObserver<NewPaymentResponse> responseObserver) {
        responseObserver.onNext(NewPaymentResponse.newBuilder().setId(UUID.randomUUID().toString()).build());
        responseObserver.onCompleted();
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
