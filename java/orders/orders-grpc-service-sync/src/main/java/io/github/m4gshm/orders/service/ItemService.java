package io.github.m4gshm.orders.service;

import io.github.m4gshm.orders.data.model.Order;

import java.util.List;

public interface ItemService {
    Double getSumCost(List<Order.Item> items);
}
