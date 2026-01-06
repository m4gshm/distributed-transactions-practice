package io.github.m4gshm.tests.stress.gatling;

import account.v1.AccountServiceOuterClass;
import account.v1.AccountServiceOuterClass.AccountListRequest;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import io.gatling.http.response.Response;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import io.github.m4gshm.grpc.client.ClientProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import warehouse.v1.WarehouseItemServiceGrpc;
import warehouse.v1.WarehouseService;
import warehouse.v1.WarehouseService.GetItemCostRequest;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static account.v1.AccountServiceGrpc.newBlockingStub;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static io.github.m4gshm.test.commons.ManagedChannelUtils.newManagedChannel;
import static io.github.m4gshm.test.commons.orders.OrderUtils.CUSTOMER_ID;
import static io.github.m4gshm.test.commons.orders.OrderUtils.getOrderItems;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newApproveRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newCreateRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newItemTopUpRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newReleaseRequest;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toMap;
import static warehouse.v1.Warehouse.Item;

@Slf4j
public class OrderFlowSimulation extends Simulation {
    public static final String API_V1 = "/api/v1";
    private static final Map<String, String> env = System.getenv();
    public static final String ORDER_URL = env.getOrDefault("ORDER_URL", "http://localhost:7080");

    public static final String ACCOUNT_ADDRESS = env.getOrDefault("ACCOUNT_ADDRESS", "localhost:9082");
    public static final String WAREHOUSE_ADDRESS = env.getOrDefault("WAREHOUSE_ADDRESS", "localhost:9081");

    {
        setUp(
                newScenario("Order Flow", httpRequestActionBuilder -> {
                    return httpRequestActionBuilder/* .silent().ignoreProtocolChecks() */;
                }, constantConcurrentUsers(40).during(30)))
                .assertions(global().failedRequests().count().lt(1L))
                .protocols(
                        http.baseUrl(ORDER_URL).warmUp(ORDER_URL).acceptHeader("application/json")
                )
                .maxDuration(ofSeconds(120));
    }

    private static BiFunction<Response, Session, Response> logResponse(String op) {
        return (response, session) -> {
            if (log.isDebugEnabled()) {
                log.debug("{} response: {}", op, response.body().string());
            }
            return response;
        };
    }

    private static ScenarioBuilder newScenario(
                                               String scenarioName,
                                               Function<HttpRequestActionBuilder, HttpRequestActionBuilder> customizer
    ) {
        var create = http("CreateOrder")
                .post(API_V1 + "/order")
                .body(StringBody(toJson(newCreateRequest(getOrderItems(), CUSTOMER_ID, false))))
                .transformResponse(logResponse("CreateOrder"))
//                .requestTimeout(ofSeconds(2))
                .check(status().is(200))
                .check(jsonPath("$.id").notNull().saveAs("id"));
        var approve = http("ApproveOrder")
                .post(API_V1 + "/order/approve")
                .body(StringBody(session -> {
                    var orderId = session.<String>get("id");
                    log.debug("ApproveOrder orderId {}", orderId);
                    return toJson(newApproveRequest(orderId, false));
                }))
                .transformResponse(logResponse("ApproveOrder"))
//                .requestTimeout(ofSeconds(5))
                .check(status().is(200))
                .check(jsonPath("$.status").is("APPROVED"));
        var release = http("ReleaseOrder")
                .post(API_V1 + "/order/release")
                .body(StringBody(session -> {
                    return toJson(newReleaseRequest(session.get("id"), false));
                }))
                .transformResponse(logResponse("ReleaseOrder"))
//                .requestTimeout(ofSeconds(5))
                .check(status().is(200))
                .check(jsonPath("$.status").is("RELEASED"));

        return scenario(scenarioName).exitBlockOnFail()
                .on(
                        customizer.apply(create),
                        customizer.apply(approve),
                        customizer.apply(release)
                );
    }

    private static PopulationBuilder newScenario(
                                                 String scenarioName,
                                                 Function<HttpRequestActionBuilder,
                                                         HttpRequestActionBuilder> customizer,
                                                 ClosedInjectionStep closedInjectionStep) {
        return newScenario1(scenarioName, customizer, closedInjectionStep, null);
    }

    private static PopulationBuilder newScenario(
                                                 String scenarioName,
                                                 Function<HttpRequestActionBuilder,
                                                         HttpRequestActionBuilder> customizer,
                                                 OpenInjectionStep openInjectionStep) {
        return newScenario1(scenarioName, customizer, null, openInjectionStep);
    }

    private static PopulationBuilder newScenario1(
                                                  String scenarioName,
                                                  Function<HttpRequestActionBuilder,
                                                          HttpRequestActionBuilder> customizer,
                                                  ClosedInjectionStep closedInjectionStep,
                                                  OpenInjectionStep openInjectionStep) {
        var scenarioBuilder = newScenario(scenarioName, customizer);
        return (closedInjectionStep != null
                ? scenarioBuilder.injectClosed(closedInjectionStep)
                : scenarioBuilder.injectOpen(openInjectionStep));
    }

    @SneakyThrows
    private static String toJson(MessageOrBuilder message) {
        return JsonFormat.printer().print(message);
    }

    @Override
    @SneakyThrows
    public void before() {
        log.info("init account url {}", ACCOUNT_ADDRESS);
        log.info("init warehouse url {}", WAREHOUSE_ADDRESS);
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

            log.info("expected requests {}, user need balance {}, current balance {}", requests, expectedUserBalance, balance);

            if (balance < expectedUserBalance) {
                var accountTopUpResponse = accountService.topUp(AccountServiceOuterClass.AccountTopUpRequest.newBuilder()
                        .setTopUp(AccountServiceOuterClass.AccountTopUpRequest.TopUp.newBuilder()
                                .setAmount(expectedUserBalance - balance)
                                .setClientId(CUSTOMER_ID)
                                .build())
                        .build());
                var balance1 = accountTopUpResponse.getBalance();
                log.info("topUp balance {}", balance1);
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

    private io.github.m4gshm.grpc.client.ClientProperties clientProperties(String address) {
        var properties = new ClientProperties();
        properties.setAddress(address);
        properties.setSecure(false);
        return properties;
    }
}
