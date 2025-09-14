package io.github.m4gshm.orders.service;

import reactor.core.publisher.Mono;

import java.util.List;

public interface ItemService {
    Mono<Double> getSumCost(List<String> itemIds);
}
