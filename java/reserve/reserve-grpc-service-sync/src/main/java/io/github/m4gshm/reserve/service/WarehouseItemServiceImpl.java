package io.github.m4gshm.reserve.service;

import io.github.m4gshm.Grpc;
import io.github.m4gshm.reserve.data.WarehouseItemStorage;
import io.grpc.stub.StreamObserver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import warehouse.v1.Warehouse;
import warehouse.v1.WarehouseItemServiceGrpc;
import warehouse.v1.WarehouseService.GetItemCostRequest;
import warehouse.v1.WarehouseService.GetItemCostResponse;
import warehouse.v1.WarehouseService.ItemListRequest;
import warehouse.v1.WarehouseService.ItemListResponse;
import warehouse.v1.WarehouseService.ItemTopUpRequest;
import warehouse.v1.WarehouseService.ItemTopUpResponse;

import static io.github.m4gshm.protobuf.TimestampUtils.toTimestamp;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WarehouseItemServiceImpl extends WarehouseItemServiceGrpc.WarehouseItemServiceImplBase {
    WarehouseItemStorage warehouseItemStorage;
    Grpc grpc;

    @Override
    public void getItemCost(GetItemCostRequest request,
                            StreamObserver<GetItemCostResponse> responseObserver) {
        grpc.subscribe("getItemCost", responseObserver, () -> {
            var id = request.getId();
            var warehouseItem = warehouseItemStorage.getById(id);
            return GetItemCostResponse.newBuilder()
                    .setCost(warehouseItem.unitCost())
                    .build();
        });
    }

    @Override
    public void itemList(ItemListRequest request, StreamObserver<ItemListResponse> responseObserver) {
        grpc.subscribe("itemList", responseObserver, () -> {
            var items = warehouseItemStorage.findAll();
            return ItemListResponse.newBuilder()
                    .addAllAccounts(items.stream()
                            .map(item -> Warehouse.Item.newBuilder()
                                    .setId(item.id())
                                    .setAmount(item.amount())
                                    .setReserved(item.reserved())
                                    .mergeUpdatedAt(toTimestamp(item.updatedAt()))
                                    .build())
                            .toList())
                    .build();
        });
    }

    @Override
    public void topUp(ItemTopUpRequest request, StreamObserver<ItemTopUpResponse> responseObserver) {
        grpc.subscribe("topUp", responseObserver, () -> {
            var topUp = request.getTopUp();
            var id = topUp.getId();
            int amount = topUp.getAmount();
            var item = warehouseItemStorage.topUp(id, amount);
            return ItemTopUpResponse.newBuilder().setAmount(item.remainder()).build();
        });
    }
}
