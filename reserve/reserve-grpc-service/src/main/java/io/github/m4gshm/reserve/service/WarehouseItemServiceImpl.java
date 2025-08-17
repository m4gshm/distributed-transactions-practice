package io.github.m4gshm.reserve.service;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static reactor.core.publisher.Mono.defer;

import org.springframework.stereotype.Service;

import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import warehouse.v1.Warehouse;
import warehouse.v1.Warehouse.GetItemCostResponse;
import warehouse.v1.Warehouse.ItemListRequest;
import warehouse.v1.Warehouse.ItemListResponse;
import warehouse.v1.WarehouseItemServiceGrpc;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WarehouseItemServiceImpl extends WarehouseItemServiceGrpc.WarehouseItemServiceImplBase {
    WarehouseItemStorage warehouseItemStorage;
    GrpcReactive grpc;

    @Override
    public void getItemCost(Warehouse.GetItemCostRequest request,
            StreamObserver<GetItemCostResponse> responseObserver) {
        grpc.subscribe(responseObserver, defer(() -> {
            var id = request.getId();
            return warehouseItemStorage.getById(id);
        }).map(warehouseItem -> {
            return GetItemCostResponse.newBuilder()
                    .setCost(warehouseItem.unitCost())
                    .build();
        }));
    }

    @Override
    public void itemList(ItemListRequest request, StreamObserver<ItemListResponse> responseObserver) {
        grpc.subscribe(responseObserver, warehouseItemStorage.findAll().map(items -> {
            return ItemListResponse.newBuilder()
                    .addAllAccounts(items.stream()
                            .map(item -> Warehouse.Item.newBuilder()
                                    .setId(item.id())
                                    .setAmount(item.amount())
                                    .setReserved(item.reserved())
                                    .setUpdatedAt(toTimestamp(item.updatedAt()))
                                    .build())
                            .toList())
                    .build();
        }));
    }
}
