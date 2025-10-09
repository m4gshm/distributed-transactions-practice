package io.github.m4gshm.test.orders;

import account.v1.AccountServiceGrpc.AccountServiceBlockingStub;
import account.v1.AccountServiceOuterClass.AccountTopUpRequest;
import com.google.protobuf.util.Timestamps;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.test.orders.config.AccountServiceConfig;
import io.github.m4gshm.test.orders.config.OrderServiceConfig;
import io.github.m4gshm.test.orders.config.WarehouseItemServiceConfig;
import orders.v1.OrderOuterClass.Order.Delivery;

import orders.v1.OrderServiceGrpc.OrderServiceBlockingStub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceBlockingStub;
import warehouse.v1.WarehouseService.GetItemCostRequest;
import warehouse.v1.WarehouseService.ItemTopUpRequest;
import warehouse.v1.WarehouseService.ItemTopUpRequest.TopUp;

import java.util.Map;

import static orders.v1.OrderOuterClass.Order.Status.APPROVED;
import static orders.v1.OrderOuterClass.Order.Status.INSUFFICIENT;
import static orders.v1.OrderOuterClass.Order.Status.RELEASED;
import static orders.v1.OrderServiceOuterClass.OrderApproveRequest;
import static orders.v1.OrderServiceOuterClass.OrderApproveResponse;
import static orders.v1.OrderServiceOuterClass.OrderCreateRequest;
import static orders.v1.OrderServiceOuterClass.OrderGetRequest;
import static orders.v1.OrderServiceOuterClass.OrderReleaseRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

;

@ActiveProfiles("test")
@SpringBootTest(classes = {
        OrdersGrpcTest.Config.class,
        AccountServiceConfig.class,
        OrderServiceConfig.class,
        WarehouseItemServiceConfig.class,
})
@EnableAutoConfiguration
public class OrdersGrpcTest {

    @Autowired
    AccountServiceBlockingStub accountService;
    @Autowired
    OrderServiceBlockingStub ordersService;
    @Autowired
    WarehouseItemServiceBlockingStub warehouseItemService;
    @Autowired
    AccountStorage accountStorage;

    private static Map<String, Integer> getOrderItems() {
        return Map.of(
                "f7c36185-f570-4e6b-b1b2-f3f0f9c46135",
                1,
                "374fabf8-dd31-4912-93b1-57d177b9f6c6",
                3
        );
    }

    private static OrderApproveRequest newApproveRequest(String orderId, boolean twoPhaseCommit) {
        return OrderApproveRequest.newBuilder()
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
                        .setDelivery(Delivery.newBuilder()
                                .setDateTime(Timestamps.parseUnchecked("2025-07-26T17:03:13.475Z"))
                                .setAddress("Lenina st., 1")
                                .setType(Delivery.Type.PICKUP)
                                .build())
                        .build())
                .setTwoPhaseCommit(twoPhaseCommit)
                .build();
    }

    private static OrderReleaseRequest newReleaseRequest(String orderId, boolean twoPhaseCommit) {
        return OrderReleaseRequest.newBuilder()
                .setId(orderId)
                .setTwoPhaseCommit(twoPhaseCommit)
                .build();
    }

    private static OrderGetRequest orderGetRequest(OrderApproveResponse orderApproveResponse) {
        return OrderGetRequest.newBuilder()
                .setId(orderApproveResponse.getId())
                .build();
    }

    private void accountTopUp(String customerId, double sumCost) {
        accountService.topUp(AccountTopUpRequest.newBuilder()
                .setTopUp(AccountTopUpRequest.TopUp
                        .newBuilder()
                        .setClientId(customerId)
                        .setAmount(sumCost)
                        .build())
                .build());
    }

    private void createApproveReleaseOrderSuccess(boolean twoPhaseCommit) {
        var items = getOrderItems();

        // populate warehouse
        populateWarehouse(items);

        var sumCost = getSumCost(items);

        var customerId = "f54e7dc2-f8aa-45bc-b632-ea0c15eaa5e2";

        // populate account
        accountTopUp(customerId, sumCost);

        var orderCreateResponse = ordersService.create(newCreateRequest(items, customerId, twoPhaseCommit));
        var orderId = orderCreateResponse.getId();

        var orderApproveResponse = ordersService.approve(newApproveRequest(orderId, twoPhaseCommit));
        assertEquals(APPROVED, orderApproveResponse.getStatus());

        var orderReleaseResponse = ordersService.release(newReleaseRequest(orderId, twoPhaseCommit));
        assertEquals(RELEASED, orderReleaseResponse.getStatus());
    }

    private double getSumCost(Map<String, Integer> items) {
        return items.entrySet().stream().mapToDouble(e -> {
            var cost = warehouseItemService.getItemCost(
                    GetItemCostRequest.newBuilder()
                            .setId(e.getKey())
                            .build())
                    .getCost();
            return cost * (double) e.getValue();
        }).sum();
    }

    private void populateWarehouse(Map<String, Integer> items) {
        items.forEach((itemId, amount) -> warehouseItemService.topUp(
                ItemTopUpRequest.newBuilder()
                        .setTopUp(TopUp.newBuilder()
                                .setId(itemId)
                                .setAmount(amount)
                                .build())
                        .build()));
    }

    @Test
    public void processOrderSuccessWithTPC() {
        createApproveReleaseOrderSuccess(true);
    }

    @Test
    public void processOrderSuccessWithoutTPC() {
        createApproveReleaseOrderSuccess(false);
    }

    @Test
    public void processOrderWithInsufficientMoneyAccountButWithDelayTopUp() throws InterruptedException {
        var items = getOrderItems();

        // populate warehouse
        populateWarehouse(items);

        var sumCost = getSumCost(items);

        var customerId = "f54e7dc2-f8aa-45bc-b632-ea0c15eaa5e2";

        // reset account
        accountStorage.getById(customerId).flatMap(account -> {
            return accountStorage.addAmount(customerId, -account.amount() + account.locked());
        }).block();

        var twoPhaseCommit = true;
        var orderCreateResponse = ordersService.create(newCreateRequest(items, customerId, twoPhaseCommit));
        var orderId = orderCreateResponse.getId();

        var orderApproveResponse = ordersService.approve(newApproveRequest(orderId, twoPhaseCommit));
        assertEquals(INSUFFICIENT, orderApproveResponse.getStatus());

        accountTopUp(customerId, sumCost);

        for (int i = 1; i <= 5; i++) {
            var status = ordersService.get(orderGetRequest(orderApproveResponse)).getOrder().getStatus();
            if (status != INSUFFICIENT) {
                break;
            }
            Thread.sleep(i * 500);
        }

        var status = ordersService.get(orderGetRequest(orderApproveResponse)).getOrder().getStatus();
        assertEquals(APPROVED, status);

        var orderReleaseResponse = ordersService.release(newReleaseRequest(orderId, twoPhaseCommit));
        assertEquals(RELEASED, orderReleaseResponse.getStatus());
    }

    @SpringBootConfiguration
    @TestConfiguration
    public static class Config {

    }

}
