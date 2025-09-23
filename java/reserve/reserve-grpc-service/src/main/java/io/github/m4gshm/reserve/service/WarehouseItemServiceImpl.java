package io.github.m4gshm.reserve.service;

import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import warehouse.v1.WarehouseService.GetItemCostRequest;
import warehouse.v1.WarehouseService.GetItemCostResponse;
import warehouse.v1.WarehouseService.ItemListRequest;
import warehouse.v1.WarehouseService.ItemListResponse;
import warehouse.v1.WarehouseService.ItemTopUpRequest;
import warehouse.v1.WarehouseService.ItemTopUpResponse;
import warehouse.v1.WarehouseItemServiceGrpc;
import warehouse.v1.Warehouse;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;
import static reactor.core.publisher.Mono.defer;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WarehouseItemServiceImpl extends WarehouseItemServiceGrpc.WarehouseItemServiceImplBase {
    WarehouseItemStorage warehouseItemStorage;
    GrpcReactive grpc;

    @Override
    public void getItemCost(GetItemCostRequest request,
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

    @Override
    public void topUp(ItemTopUpRequest request, StreamObserver<ItemTopUpResponse> responseObserver) {
        grpc.subscribe(responseObserver, defer(() -> {
            var topUp = request.getTopUp();
            var id = topUp.getId();
            int amount = topUp.getAmount();
            return warehouseItemStorage.topUp(id, amount).map(item -> {
                return ItemTopUpResponse.newBuilder().setAmount(item.remainder()).build();
            });
        }));
    }
}
