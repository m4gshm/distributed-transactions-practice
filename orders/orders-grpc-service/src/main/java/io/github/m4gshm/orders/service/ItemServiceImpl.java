package io.github.m4gshm.orders.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import warehouse.v1.Warehouse;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceStub;

import java.util.List;

import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static reactor.core.publisher.Flux.fromIterable;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final WarehouseItemServiceStub warehouseClient;

    @Override
    public Mono<Double> getSumCost(List<String> itemIds) {
        return fromIterable(itemIds).flatMap(itemId -> {
            return toMono(Warehouse.GetItemCostRequest.newBuilder()
                                                      .setId(itemId)
                                                      .build(),
                          warehouseClient::getItemCost);
        }).map(Warehouse.GetItemCostResponse::getCost).reduce(0.0, Double::sum);
    }
}
