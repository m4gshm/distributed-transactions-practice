package reserve.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reserve.data.ReserveStorage;
import reserve.data.model.Reserve;
import reserve.data.model.Reserve.Status;
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

import static reactive.GrpcUtils.subscribe;
import static reserve.data.model.Reserve.Item;

@Slf4j
@RequiredArgsConstructor
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    private final ReserveStorage reserveStorage;

    @Override
    public void create(NewReserveRequest request, StreamObserver<NewReserveResponse> response) {
        subscribe(response, Mono.defer(() -> {
            var paymentId = UUID.randomUUID().toString();
            var body = request.getBody();
            var items = body.getItemsList().stream().map(item -> Item.builder()
                            .id(item.getId())
                            .count(item.getCount())
                            .build())
                    .toList();
            var reserve = Reserve.builder()
                    .id(paymentId)
                    .externalRef(body.getExternalRef())
                    .status(Status.CREATED)
                    .items(items)
                    .build();
            return reserveStorage.save(reserve, request.getTwoPhaseCommit())
                    .thenReturn(NewReserveResponse.newBuilder().setId(paymentId).build());
        }));
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
