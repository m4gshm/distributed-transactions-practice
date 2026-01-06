package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.model.Order;
import io.micrometer.observation.ObservationRegistry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceStub;

import java.util.List;

import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static reactor.core.observability.micrometer.Micrometer.observation;
import static reactor.core.publisher.Flux.fromIterable;
import static warehouse.v1.WarehouseService.GetItemCostRequest;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ItemServiceImpl implements ItemService {
    WarehouseItemServiceStub warehouseClient;
    ObservationRegistry observationRegistry;

    @Override
    public Mono<Double> getSumCost(List<Order.Item> items) {
        return fromIterable(items).flatMap(item -> toMono(
                "warehouseClient::getItemCost",
                GetItemCostRequest.newBuilder()
                        .setId(item.id())
                        .build(),
                warehouseClient::getItemCost
        ).map(response -> response.getCost() * item.amount()))
                .reduce(0.0, Double::sum)
                .name("getSumCost")
                .tap(observation(observationRegistry));
    }
}
