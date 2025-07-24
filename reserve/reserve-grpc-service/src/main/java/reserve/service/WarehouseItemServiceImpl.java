package reserve.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import payment.v1.ItemServiceGrpc;
import payment.v1.Warehouse;
import payment.v1.Warehouse.ItemListRequest;
import payment.v1.Warehouse.ItemListResponse;
import reserve.data.WarehouseItemStorage;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static io.github.m4gshm.reactive.GrpcUtils.subscribe;

@Service
@RequiredArgsConstructor
public class WarehouseItemServiceImpl extends ItemServiceGrpc.ItemServiceImplBase {
    private final WarehouseItemStorage warehouseItemStorage;

    @Override
    public void list(ItemListRequest request, StreamObserver<ItemListResponse> responseObserver) {
        subscribe(responseObserver, warehouseItemStorage.findAll().map(items -> {
            return ItemListResponse.newBuilder()
                    .addAllAccounts(items.stream().map(item -> Warehouse.Item.newBuilder()
                            .setId(item.id())
                            .setAmount(item.amount())
                            .setReserved(item.reserved())
                            .setUpdatedAt(toTimestamp(item.updatedAt()))
                            .build()).toList())
                    .build();
        }));
    }
}
