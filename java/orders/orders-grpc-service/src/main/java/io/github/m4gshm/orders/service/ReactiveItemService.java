package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.model.Order;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ReactiveItemService {
    Mono<Double> getSumCost(List<Order.Item> items);
}
