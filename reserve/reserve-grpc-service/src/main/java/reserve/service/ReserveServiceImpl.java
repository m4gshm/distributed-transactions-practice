package reserve.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import reserve.v1.Reserve;
import reserve.v1.Reserve.CancelReserveResponse;
import reserve.v1.Reserve.FindReserveResponse;
import reserve.v1.Reserve.NewReserveResponse;
import reserve.v1.Reserve.UpdateReserveResponse;
import reserve.v1.ReserveServiceGrpc;

@RequiredArgsConstructor
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    @Override
    public void create(Reserve.NewReserveRequest request, StreamObserver<NewReserveResponse> responseObserver) {
        super.create(request, responseObserver);
    }

    @Override
    public void cancel(Reserve.CancelReserveRequest request, StreamObserver<CancelReserveResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void update(Reserve.UpdateReserveRequest request, StreamObserver<UpdateReserveResponse> responseObserver) {
        super.update(request, responseObserver);
    }

    @Override
    public void search(Reserve.FindReserveRequest request, StreamObserver<FindReserveResponse> responseObserver) {
        super.search(request, responseObserver);
    }
}
