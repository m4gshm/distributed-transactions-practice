package io.github.m4gshm.test.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;

import com.google.protobuf.util.Timestamps;

import io.github.m4gshm.test.orders.config.AccountServiceConfig;
import io.github.m4gshm.test.orders.config.OrderServiceConfig;
import io.github.m4gshm.test.orders.config.WarehouseItemServiceConfig;
import orders.v1.Orders;
import orders.v1.Orders.Order.Status;
import orders.v1.Orders.OrderCreateRequest;
import orders.v1.OrdersServiceGrpc.OrdersServiceBlockingStub;
import payment.v1.AccountOuterClass;
import payment.v1.AccountServiceGrpc.AccountServiceBlockingStub;
import warehouse.v1.Warehouse;
import warehouse.v1.Warehouse.ItemTopUpRequest;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceBlockingStub;

@ActiveProfiles("test")
@SpringBootTest(classes = {
        OrdersGrpcTest.Config.class,
        AccountServiceConfig.class,
        OrderServiceConfig.class,
        WarehouseItemServiceConfig.class,
})
public class OrdersGrpcTest {

    @Autowired
    AccountServiceBlockingStub accountService;
    @Autowired
    OrdersServiceBlockingStub ordersService;
    @Autowired
    WarehouseItemServiceBlockingStub warehouseItemService;

    private static Orders.OrderApproveRequest newApproveRequest(String orderId, boolean twoPhaseCommit) {
        return Orders.OrderApproveRequest.newBuilder()
                .setId(orderId)
                .setTwoPhaseCommit(twoPhaseCommit)
                .build();
    }

    private static OrderCreateRequest newCreateRequest(Map<String, Integer> items,
                                                       String customerId,
                                                       boolean twoPhaseCommit
    ) {
        return OrderCreateRequest.newBuilder()
                .setBody(OrderCreateRequest.OrderCreate.newBuilder()
                        .addAllItems(items
                                .entrySet()
                                .stream()
                                .map(e -> {
                                    return OrderCreateRequest.OrderCreate.Item.newBuilder()
                                            .setId(e.getKey())
                                            .setAmount(e.getValue())
                                            .build();
                                })
                                .toList())
                        .setCustomerId(customerId)
                        .setDelivery(Orders.Order.Delivery.newBuilder()
                                .setDateTime(Timestamps.parseUnchecked("2025-07-26T17:03:13.475Z"))
                                .setAddress("Lenina st., 1")
                                .setType(Orders.Order.Delivery.Type.PICKUP)
                                .build())
                        .build())
                .setTwoPhaseCommit(twoPhaseCommit)
                .build();
    }

    private static Orders.OrderReleaseRequest newReleaseRequest(String orderId, boolean twoPhaseCommit) {
        return Orders.OrderReleaseRequest.newBuilder()
                .setId(orderId)
                .setTwoPhaseCommit(twoPhaseCommit)
                .build();
    }

    private void createApproveReleaseOrderSuccess(boolean twoPhaseCommit) {
        var items = Map.of(
                "f7c36185-f570-4e6b-b1b2-f3f0f9c46135",
                1,
                "374fabf8-dd31-4912-93b1-57d177b9f6c6",
                3
        );

        // populate warehouse
        items.forEach((itemId, amount) -> warehouseItemService.topUp(ItemTopUpRequest.newBuilder()
                .setTopUp(ItemTopUpRequest.TopUp.newBuilder()
                        .setId(itemId)
                        .setAmount(amount)
                        .build())
                .build()));

        var sumCost = items.entrySet().stream().mapToDouble(e -> {
            var cost = warehouseItemService.getItemCost(
                    Warehouse.GetItemCostRequest.newBuilder()
                            .setId(e.getKey())
                            .build())
                    .getCost();
            return cost * (double) e.getValue();
        }).sum();

        var customerId = "f54e7dc2-f8aa-45bc-b632-ea0c15eaa5e2";

        // populate account
        accountService.topUp(AccountOuterClass.AccountTopUpRequest.newBuilder()
                .setTopUp(AccountOuterClass.AccountTopUpRequest.TopUp
                        .newBuilder()
                        .setClientId(customerId)
                        .setAmount(sumCost)
                        .build())
                .build());

        var orderCreateResponse = ordersService.create(newCreateRequest(items, customerId, twoPhaseCommit));
        var orderId = orderCreateResponse.getId();

        var orderApproveResponse = ordersService.approve(newApproveRequest(orderId, twoPhaseCommit));
        assertEquals(Status.APPROVED, orderApproveResponse.getStatus());

        var orderReleaseResponse = ordersService.release(newReleaseRequest(orderId, twoPhaseCommit));
        assertEquals(Status.RELEASED, orderReleaseResponse.getStatus());
    }

    @Test
    public void createApproveReleaseOrderSuccessWithTPC() {
        createApproveReleaseOrderSuccess(true);
    }

    @Test
    public void createApproveReleaseOrderSuccessWithoutTPC() {
        createApproveReleaseOrderSuccess(false);
    }

    @SpringBootConfiguration
    @TestConfiguration
    public static class Config {

    }

}
