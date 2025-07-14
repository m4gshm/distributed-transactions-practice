package reserve.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import reserve.v1.Reserve;
import reserve.v1.Reserve.CancelReserveRequest;
import reserve.v1.Reserve.CancelReserveResponse;
import reserve.v1.Reserve.FindReserveRequest;
import reserve.v1.Reserve.FindReserveResponse;
import reserve.v1.Reserve.NewReserveRequest;
import reserve.v1.Reserve.NewReserveResponse;
import reserve.v1.Reserve.UpdateReserveRequest;
import reserve.v1.Reserve.UpdateReserveResponse;
import reserve.v1.ReserveServiceGrpc;

import java.util.UUID;

@RequiredArgsConstructor
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    @Override
    public void create(NewReserveRequest request, StreamObserver<NewReserveResponse> responseObserver) {
        responseObserver.onNext(NewReserveResponse.newBuilder().setId(UUID.randomUUID().toString()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void cancel(CancelReserveRequest request, StreamObserver<CancelReserveResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void update(UpdateReserveRequest request, StreamObserver<UpdateReserveResponse> responseObserver) {
        super.update(request, responseObserver);
    }

    @Override
    public void search(FindReserveRequest request, StreamObserver<FindReserveResponse> responseObserver) {
        super.search(request, responseObserver);
    }
}
