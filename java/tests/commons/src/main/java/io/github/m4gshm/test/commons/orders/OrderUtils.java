package io.github.m4gshm.test.commons.orders;

import account.v1.AccountServiceOuterClass.AccountTopUpRequest;
import com.google.protobuf.util.Timestamps;
import lombok.experimental.UtilityClass;
import orders.v1.OrderOuterClass.Order.Delivery;
import orders.v1.OrderServiceOuterClass;
import orders.v1.OrderServiceOuterClass.OrderApproveResponse;
import orders.v1.OrderServiceOuterClass.OrderCreateRequest;
import warehouse.v1.WarehouseService.ItemTopUpRequest;
import warehouse.v1.WarehouseService.ItemTopUpRequest.TopUp;

import java.util.Map;

@UtilityClass
public class OrderUtils {

    public static final String CUSTOMER_ID = "f54e7dc2-f8aa-45bc-b632-ea0c15eaa5e2";

    public static Map<String, Integer> getOrderItems() {
        return Map.of(
                "f7c36185-f570-4e6b-b1b2-f3f0f9c46135",
                1,
                "374fabf8-dd31-4912-93b1-57d177b9f6c6",
                3
        );
    }

    public static AccountTopUpRequest newAccountTopUpRequest(String customerId, double sumCost) {
        return AccountTopUpRequest.newBuilder()
                .setTopUp(AccountTopUpRequest.TopUp
                        .newBuilder()
                        .setClientId(customerId)
                        .setAmount(sumCost)
                        .build())
                .build();
    }

    public static OrderServiceOuterClass.OrderApproveRequest newApproveRequest(String orderId, boolean twoPhaseCommit) {
        return OrderServiceOuterClass.OrderApproveRequest.newBuilder()
                .setId(orderId)
                .setTwoPhaseCommit(twoPhaseCommit)
                .build();
    }

    public static OrderCreateRequest newCreateRequest(Map<String, Integer> items,
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

    public static ItemTopUpRequest newItemTopUpRequest(String itemId, Integer amount) {
        return ItemTopUpRequest.newBuilder()
                .setTopUp(TopUp.newBuilder()
                        .setId(itemId)
                        .setAmount(amount)
                        .build())
                .build();
    }

    public static OrderServiceOuterClass.OrderReleaseRequest newReleaseRequest(String orderId, boolean twoPhaseCommit) {
        return OrderServiceOuterClass.OrderReleaseRequest.newBuilder()
                .setId(orderId)
                .setTwoPhaseCommit(twoPhaseCommit)
                .build();
    }

    public static OrderServiceOuterClass.OrderGetRequest orderGetRequest(
                                                                         OrderApproveResponse orderApproveResponse
    ) {
        return OrderServiceOuterClass.OrderGetRequest.newBuilder()
                .setId(orderApproveResponse.getId())
                .build();
    }
}
