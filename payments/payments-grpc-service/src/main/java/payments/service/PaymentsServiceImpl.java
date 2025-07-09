package payments.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import payments.v1.Payments;
import payments.v1.PaymentsServiceGrpc;

@RequiredArgsConstructor
public class PaymentsServiceImpl extends PaymentsServiceGrpc.PaymentsServiceImplBase {
    @Override
    public void create(Payments.NewPaymentRequest request, StreamObserver<Payments.NewPaymentResponse> responseObserver) {
        super.create(request, responseObserver);
    }

    @Override
    public void cancel(Payments.CancelPaymentRequest request, StreamObserver<Payments.CancelPaymentResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void process(Payments.ProcessPaymentRequest request, StreamObserver<Payments.ProcessPaymentResponse> responseObserver) {
        super.process(request, responseObserver);
    }
}
