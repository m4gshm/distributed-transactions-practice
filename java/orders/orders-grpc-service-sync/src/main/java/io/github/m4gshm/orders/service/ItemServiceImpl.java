package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceBlockingStub;

import java.util.List;

import static warehouse.v1.WarehouseService.GetItemCostRequest;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final WarehouseItemServiceBlockingStub warehouseClient;

    @Override
    public Double getSumCost(List<Order.Item> items) {
        return items.stream().map(item -> {
            var request = GetItemCostRequest.newBuilder()
                    .setId(item.id())
                    .build();

            var itemCost = warehouseClient.getItemCost(request);
            return itemCost.getCost() * item.amount();
        }).reduce(0.0, Double::sum);
    }
}
