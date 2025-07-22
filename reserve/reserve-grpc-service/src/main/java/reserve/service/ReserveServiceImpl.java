package reserve.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reserve.data.ReserveStorage;
import reserve.data.model.Reserve;
import reserve.data.model.Reserve.Status;
import reserve.v1.Reserve.ReserveCancelRequest;
import reserve.v1.Reserve.ReserveCancelResponse;
import reserve.v1.Reserve.FindReserveRequest;
import reserve.v1.Reserve.FindReserveResponse;
import reserve.v1.Reserve.ReserveCreateRequest;
import reserve.v1.Reserve.ReserveCreateResponse;
import reserve.v1.Reserve.ReserveUpdateRequest;
import reserve.v1.Reserve.ReserveUpdateResponse;
import reserve.v1.ReserveServiceGrpc;

import java.util.UUID;

import static io.github.m4gshm.reactive.GrpcUtils.subscribe;
import static reserve.data.model.Reserve.Item;

@Slf4j
@RequiredArgsConstructor
public class ReserveServiceImpl extends ReserveServiceGrpc.ReserveServiceImplBase {
    private final ReserveStorage reserveStorage;

    @Override
    public void create(ReserveCreateRequest request, StreamObserver<ReserveCreateResponse> response) {
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
                    .thenReturn(ReserveCreateResponse.newBuilder().setId(paymentId).build());
        }));
    }

    @Override
    public void cancel(ReserveCancelRequest request, StreamObserver<ReserveCancelResponse> responseObserver) {
        super.cancel(request, responseObserver);
    }

    @Override
    public void update(ReserveUpdateRequest request, StreamObserver<ReserveUpdateResponse> responseObserver) {
        super.update(request, responseObserver);
    }

    @Override
    public void search(FindReserveRequest request, StreamObserver<FindReserveResponse> responseObserver) {
        super.search(request, responseObserver);
    }
}
