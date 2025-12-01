package io.github.m4gshm.test.orders;

import account.v1.AccountServiceGrpc.AccountServiceBlockingStub;
import io.github.m4gshm.payments.data.AccountStorage;
import io.github.m4gshm.test.commons.orders.OrderUtils;
import io.github.m4gshm.test.orders.config.AccountServiceConfig;
import io.github.m4gshm.test.orders.config.OrderServiceConfig;
import io.github.m4gshm.test.orders.config.WarehouseItemServiceConfig;
import orders.v1.OrderServiceGrpc.OrderServiceBlockingStub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import warehouse.v1.Warehouse;
import warehouse.v1.WarehouseItemServiceGrpc.WarehouseItemServiceBlockingStub;
import warehouse.v1.WarehouseService;
import warehouse.v1.WarehouseService.GetItemCostRequest;

import java.util.Map;
import java.util.function.Function;

import static io.github.m4gshm.test.commons.orders.OrderUtils.CUSTOMER_ID;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newAccountTopUpRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newApproveRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newCreateRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newItemTopUpRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newReleaseRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.orderGetRequest;
import static java.util.stream.Collectors.toMap;
import static orders.v1.OrderOuterClass.Order.Status.APPROVED;
import static orders.v1.OrderOuterClass.Order.Status.INSUFFICIENT;
import static orders.v1.OrderOuterClass.Order.Status.RELEASED;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    public void accountTopUp(String customerId, double sumCost) {
        accountService.topUp(newAccountTopUpRequest(customerId, sumCost));
    }

    private void createApproveReleaseOrderSuccess(boolean twoPhaseCommit) {
        var items = OrderUtils.getOrderItems();

        var warehouseItems1 = getWarehouseVal(Warehouse.Item::getAmount);

        // populate warehouse
        populateWarehouse(items);

        var warehouseItems2 = getWarehouseVal(Warehouse.Item::getAmount);

        for (var itemID : items.keySet()) {
            var before = warehouseItems1.get(itemID);
            var after = warehouseItems2.get(itemID);
            assertEquals(items.get(itemID), after - before);
        }

        var sumCost = getSumCost(items);

        var customerId = CUSTOMER_ID;

        // populate account
        accountTopUp(customerId, sumCost);

        var orderCreateResponse = ordersService.create(newCreateRequest(items, customerId, twoPhaseCommit));
        var orderId = orderCreateResponse.getId();

        var orderApproveResponse = ordersService.approve(newApproveRequest(orderId, twoPhaseCommit));
        assertEquals(APPROVED, orderApproveResponse.getStatus());

        var warehouseItems3 = getWarehouseVal(Warehouse.Item::getReserved);

        for (var itemID : items.keySet()) {
            var reserved = warehouseItems3.get(itemID);
            assertEquals(items.get(itemID), reserved);
        }

        var orderReleaseResponse = ordersService.release(newReleaseRequest(orderId, twoPhaseCommit));
        assertEquals(RELEASED, orderReleaseResponse.getStatus());

        var warehouseItems4 = getWarehouseVal(Warehouse.Item::getAmount);

        for (var itemID : items.keySet()) {
            var before = warehouseItems1.get(itemID);
            var after = warehouseItems4.get(itemID);
            assertEquals(0, after - before);
        }
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

    private Map<String, Integer> getWarehouseVal(Function<Warehouse.Item, Integer> getItemValue) {
        return this.warehouseItemService.itemList(WarehouseService.ItemListRequest
                .newBuilder()
                .build()
        ).getAccountsList().stream().collect(toMap(Warehouse.Item::getId, getItemValue));
    }

    private void populateWarehouse(Map<String, Integer> items) {
        items.forEach((itemId, amount) -> warehouseItemService.topUp(
                newItemTopUpRequest(itemId, amount)));
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
        var items = OrderUtils.getOrderItems();

        // populate warehouse
        populateWarehouse(items);

        var sumCost = getSumCost(items);

        var customerId = CUSTOMER_ID;

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
