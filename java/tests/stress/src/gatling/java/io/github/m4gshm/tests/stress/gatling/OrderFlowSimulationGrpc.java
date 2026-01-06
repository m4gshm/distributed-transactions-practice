package io.github.m4gshm.tests.stress.gatling;

import account.v1.AccountServiceOuterClass;
import account.v1.AccountServiceOuterClass.AccountListRequest;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.grpc.GrpcUnaryActionBuilder;
import io.github.m4gshm.grpc.client.ClientProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import orders.v1.OrderServiceGrpc;
import orders.v1.OrderServiceOuterClass.OrderApproveResponse;
import orders.v1.OrderServiceOuterClass.OrderCreateResponse;
import orders.v1.OrderServiceOuterClass.OrderReleaseResponse;
import warehouse.v1.WarehouseItemServiceGrpc;
import warehouse.v1.WarehouseService;
import warehouse.v1.WarehouseService.GetItemCostRequest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static account.v1.AccountServiceGrpc.newBlockingStub;
import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.grpc.GrpcDsl.grpc;
import static io.gatling.javaapi.grpc.GrpcDsl.response;
import static io.gatling.javaapi.grpc.GrpcDsl.statusCode;
import static io.github.m4gshm.test.commons.ManagedChannelUtils.newManagedChannel;
import static io.github.m4gshm.test.commons.orders.OrderUtils.CUSTOMER_ID;
import static io.github.m4gshm.test.commons.orders.OrderUtils.getOrderItems;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newApproveRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newCreateRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newItemTopUpRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newReleaseRequest;
import static io.grpc.Status.Code.OK;
import static java.time.Duration.ofSeconds;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static orders.v1.OrderOuterClass.Order.Status.APPROVED;
import static orders.v1.OrderOuterClass.Order.Status.RELEASED;
import static warehouse.v1.Warehouse.Item;

@Slf4j
public class OrderFlowSimulationGrpc extends Simulation {
    private static final Map<String, String> env = System.getenv();
    public static final String ACCOUNT_ADDRESS = env.getOrDefault("ACCOUNT_ADDRESS", "localhost:9082");
    public static final String WAREHOUSE_ADDRESS = env.getOrDefault("WAREHOUSE_ADDRESS", "localhost:9081");
    public static final String ORDER_ADDRESS = env.getOrDefault("ORDER_ADDRESS", "localhost:9080");

    {
        setUp(
                newScenario("Order Flow",
                        builder -> builder,
                        constantConcurrentUsers(1).during(30)
                )
        )
                .assertions(global().failedRequests().count().lt(1L))
                .protocols(
                        grpc.serverConfigurations(
                                grpc
                                        .serverConfiguration("order")
                                        .forTarget(ORDER_ADDRESS)
                                        .usePlaintext()
                        )
                )
                .maxDuration(ofSeconds(120));
    }

    @Override
    @SneakyThrows
    public void before() {
        var accountChannel = newManagedChannel(clientProperties(ACCOUNT_ADDRESS), List.of());
        var warehouseChannel = newManagedChannel(clientProperties(WAREHOUSE_ADDRESS), List.of());
        try {
            var accountService = newBlockingStub(accountChannel);
            var warehouseItemService = WarehouseItemServiceGrpc.newBlockingStub(warehouseChannel);

            double balance = accountService.list(AccountListRequest.newBuilder().build())
                    .getAccountsList()
                    .stream()
                    .filter(a -> CUSTOMER_ID.equals(a.getClientId()))
                    .map(a -> a.getAmount() - a.getLocked())
                    .reduce(0.0, Double::sum);
            log.info("balance {}", balance);

            int requests = 100_000_000;

            var orderItems = getOrderItems();
            var sumCostOfRequest = orderItems.entrySet().stream().mapToDouble(e -> {
                var id = e.getKey();
                var amount = e.getValue();

                var cost = warehouseItemService.getItemCost(GetItemCostRequest.newBuilder().setId(id).build())
                        .getCost();
                return cost * amount;
            }).sum();

            double expectedUserBalance = sumCostOfRequest * requests;

            log.info("expected requests {}, user balance {}", requests, expectedUserBalance);

            if (balance < expectedUserBalance) {
                accountService.topUp(AccountServiceOuterClass.AccountTopUpRequest.newBuilder()
                        .setTopUp(AccountServiceOuterClass.AccountTopUpRequest.TopUp.newBuilder()
                                .setAmount(expectedUserBalance - balance)
                                .setClientId(CUSTOMER_ID)
                                .build())
                        .build());
            }

            var itemAmounts = warehouseItemService.itemList(WarehouseService.ItemListRequest.newBuilder()
                    .build()).getAccountsList().stream().collect(toMap(Item::getId, a -> a));

            orderItems.forEach((id, count) -> {
                int amount = itemAmounts.get(id).getAmount();
                if (amount < (requests * count)) {
                    var itemTopUpRequest = newItemTopUpRequest(id, (requests * count) - amount);
                    warehouseItemService.topUp(itemTopUpRequest);
                }
            });
        } finally {
            accountChannel.shutdownNow();
            warehouseChannel.shutdownNow();
        }
    }

    private ClientProperties clientProperties(String address) {
        var properties = new ClientProperties();
        properties.setAddress(address);
        properties.setSecure(false);
        return properties;
    }

    private ScenarioBuilder newScenario(
                                        String scenarioName,
                                        Function<GrpcUnaryActionBuilder<?, ?>, GrpcUnaryActionBuilder<?, ?>> customizer
    ) {

        var orderCreateRequest = newCreateRequest(getOrderItems(), CUSTOMER_ID, false);
        var create = grpc("CreateOrder")
                .unary(OrderServiceGrpc.getCreateMethod())
                .send(orderCreateRequest)
                .check(statusCode().is(OK))
                .check(response(OrderCreateResponse::getId).notNull().saveAs("id"));

        var approve = grpc("ApproveOrder")
                .unary(OrderServiceGrpc.getApproveMethod())
                .send(session -> {
                    return newApproveRequest(session.get("id"), false);
                })
                .check(statusCode().is(OK))
                .check(response(OrderApproveResponse::getStatus).is(APPROVED));

        var release = grpc("ReleaseOrder")
                .unary(OrderServiceGrpc.getReleaseMethod())
                .send(session -> {
                    return newReleaseRequest(session.get("id"), false);
                })
                .check(statusCode().is(OK))
                .check(response(OrderReleaseResponse::getStatus).is(RELEASED));

        return scenario(scenarioName).exitBlockOnFail()
                .on(
                        customizer.apply(create),
                        customizer.apply(approve),
                        customizer.apply(release)
                );
    }

    private PopulationBuilder newScenario(
                                          String scenarioName,
                                          Function<GrpcUnaryActionBuilder<?, ?>,
                                                  GrpcUnaryActionBuilder<?, ?>> customizer,
                                          ClosedInjectionStep closedInjectionStep) {
        return newScenario1(scenarioName, customizer, closedInjectionStep, null);
    }

    private PopulationBuilder newScenario(
                                          String scenarioName,
                                          Function<GrpcUnaryActionBuilder<?, ?>,
                                                  GrpcUnaryActionBuilder<?, ?>> customizer,
                                          OpenInjectionStep openInjectionStep) {
        return newScenario1(scenarioName, customizer, null, openInjectionStep);
    }

    private PopulationBuilder newScenario1(
                                           String scenarioName,
                                           Function<GrpcUnaryActionBuilder<?, ?>,
                                                   GrpcUnaryActionBuilder<?, ?>> customizer,
                                           ClosedInjectionStep closedInjectionStep,
                                           OpenInjectionStep openInjectionStep) {
        var scenarioBuilder = newScenario(scenarioName, customizer);
        return (closedInjectionStep != null
                ? scenarioBuilder.injectClosed(closedInjectionStep)
                : scenarioBuilder.injectOpen(openInjectionStep));
    }
}
