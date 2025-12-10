package io.github.m4gshm.tests.stress.gatling;

import com.google.protobuf.Message;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import warehouse.v1.WarehouseService.GetItemCostResponse;
import warehouse.v1.WarehouseService.ItemListResponse.Builder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static account.v1.AccountServiceOuterClass.AccountListResponse;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static io.github.m4gshm.test.commons.orders.OrderUtils.CUSTOMER_ID;
import static io.github.m4gshm.test.commons.orders.OrderUtils.getOrderItems;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newAccountTopUpRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newApproveRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newCreateRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newItemTopUpRequest;
import static io.github.m4gshm.test.commons.orders.OrderUtils.newReleaseRequest;
import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.net.http.HttpRequest.newBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static warehouse.v1.Warehouse.Item;
import static warehouse.v1.WarehouseService.ItemListResponse;

@Slf4j
public class OrderFlowSimulation extends Simulation {
    public static final String API_V1 = "/api/v1";
    private static final Map<String, String> env = System.getenv();
    public static final String ORDER_URL = env.getOrDefault("ORDER_URL", "http://localhost:7080");
    public static final String WAREHOUSE_URL = env.getOrDefault("WAREHOUSE_URL", "http://localhost:7081");
    public static final String WAREHOUSE_ITEM_URL = WAREHOUSE_URL + API_V1 + "/warehouse/item";
    public static final String WAREHOUSE_ITEM_COST_URL = WAREHOUSE_URL + API_V1 + "/warehouse/item/cost";
    public static final String ACCOUNT_URL = env.getOrDefault("ACCOUNT_URL", "http://localhost:7082");

    {
        setUp(
                newScenario("WarmUp", httpRequestActionBuilder -> {
                    return httpRequestActionBuilder.silent().ignoreProtocolChecks();
                }, constantConcurrentUsers(100).during(30)
                ).andThen(
                        newScenario("Order Flow", identity(), rampConcurrentUsers(4).to(20).during(ofSeconds(30)))
                )
        )
                .assertions(global().failedRequests().count().lt(1L))
                .protocols(
                        http.baseUrl(ORDER_URL).warmUp(ORDER_URL).acceptHeader("application/json")
                )
                .maxDuration(ofSeconds(120));
    }

    private static <T> HttpResponse<T> check(String service, HttpResponse<T> response) {
        var body = response.body();
        var headers = response.headers();

        log.info("{} response status {}, headers {}, body {}", service, response.statusCode(), headers, body);

        if (response.statusCode() != 200) {
            var request = response.request();
            throw new IllegalStateException("unexpected status of response: status [" + response.statusCode()
                    + "] request ["
                    +
                    request.method()
                    + ":"
                    + request.uri()
                    + "], response: "
                    + body);
        }
        return response;
    }

    @SneakyThrows
    private static double getWarehouseItemCost(HttpClient httpClient, String itemId) {
        var service = "warehouse";

        var uri = new URI(WAREHOUSE_ITEM_COST_URL + "/" + itemId);

        return parse(check(service, httpClient.send(newBuilder().uri(uri).GET().build(), BodyHandlers.ofString()))
                .body(),
                GetItemCostResponse.newBuilder(),
                GetItemCostResponse.Builder::build
        ).getCost();
    }

    private static BiFunction<Response, Session, Response> logResponse(String op) {
        return (response, session) -> {
            log.info("{} response: {}", op, response.body().string());
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
                .check(status().is(200))
                .check(jsonPath("$.id").notNull().saveAs("id"));
        var approve = http("ApproveOrder")
                .post(API_V1 + "/order/approve")
                .body(StringBody(session -> {
                    return toJson(newApproveRequest(session.get("id"), false));
                }))
                .transformResponse(logResponse("ApproveOrder"))
                .check(status().is(200))
                .check(jsonPath("$.status").is("APPROVED"));
        var release = http("ReleaseOrder")
                .post(API_V1 + "/order/release")
                .body(StringBody(session -> {
                    return toJson(newReleaseRequest(session.get("id"), false));
                }))
                .transformResponse(logResponse("ReleaseOrder"))
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
    private static <T, B extends Message.Builder> T parse(String json, B builder, Function<B, T> build) {
        JsonFormat.parser().merge(json, builder);
        return build.apply(builder);
    }

    @SneakyThrows
    private static String toJson(MessageOrBuilder message) {
        return JsonFormat.printer().print(message);
    }

    @SneakyThrows
    private static void topUpWarehouseItem(String service,
                                           HttpClient httpClient,
                                           String id,
                                           Integer count,
                                           URI uri,
                                           Map<String, Item> amounts) {
        int amount = amounts.get(id).getAmount();
        if (amount < count) {
            check(service,
                    httpClient.send(newBuilder()
                            .uri(uri)
                            .PUT(ofString(toJson(newItemTopUpRequest(id, count - amount)), UTF_8))
                            .build(), BodyHandlers.ofString())
            );
        }
    }

    @SneakyThrows
    private static void topUpWarehouseItems(HttpClient httpClient,
                                            Map<String, Integer> orderItemsPerRequest,
                                            int requests) {
        var service = "warehouse";

        var uri = new URI(WAREHOUSE_ITEM_URL);
        var itemAmounts = parse(check(service,
                httpClient.send(newBuilder().uri(uri).GET().build(), BodyHandlers.ofString())).body(),
                ItemListResponse.newBuilder(),
                Builder::build
        ).getAccountsList().stream().collect(toMap(Item::getId, a -> a));

        orderItemsPerRequest.forEach((id, count) -> {
            topUpWarehouseItem(service, httpClient, id, requests * count, uri, itemAmounts);
        });
    }

    @Override
    @SneakyThrows
    public void before() {
        try (var httpClient = newHttpClient()) {
            var service = "account";
            var accountUrl = new URI(ACCOUNT_URL + API_V1 + "/account");

            var response = check(service,
                    httpClient.send(newBuilder()
                            .uri(accountUrl)
                            .GET()
                            .build(), BodyHandlers.ofString()));

            double balance = parse(response.body(),
                    AccountListResponse.newBuilder(),
                    AccountListResponse.Builder::build).getAccountsList()
                    .stream()
                    .filter(a -> CUSTOMER_ID.equals(a.getClientId()))
                    .map(a -> a.getAmount() - a.getLocked())
                    .reduce(0.0, Double::sum);

            int requests = 100_000_000;

            var orderItems = getOrderItems();
            var sumCostOfRequest = orderItems.entrySet().stream().mapToDouble(e -> {
                var id = e.getKey();
                var amount = e.getValue();
                var cost = getWarehouseItemCost(httpClient, id);
                return cost * amount;
            }).sum();

            double expectedUserBalance = sumCostOfRequest * requests;

            log.info("expected requests {}, user balance {}", requests, expectedUserBalance);

            if (balance < expectedUserBalance) {
                check(service,
                        httpClient.send(newBuilder()
                                .uri(accountUrl)
                                .PUT(ofString(toJson(newAccountTopUpRequest(CUSTOMER_ID,
                                        expectedUserBalance - balance)), UTF_8))
                                .build(),
                                BodyHandlers.ofString())
                );
            }
            topUpWarehouseItems(httpClient, orderItems, requests);
        }
    }
}
