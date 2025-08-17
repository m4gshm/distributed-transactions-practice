package io.github.m4gshm.orders.service.event;

import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.service.OrdersService;
import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import io.github.m4gshm.reactive.idempotent.consumer.MessageImpl;
import io.github.m4gshm.reactive.idempotent.consumer.MessageStorage;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders.OrderApproveResponse;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import payment.v1.PaymentOuterClass;
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Set;

import static io.github.m4gshm.orders.data.model.Order.Status.insufficient;
import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.empty;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KafkaAccountBalanceEventListenerServiceImpl {

    ReactiveKafkaConsumerTemplate<String, AccountBalanceEvent> reactiveKafkaConsumerTemplate;

    OrderStorage orderStorage;
    OrdersService ordersService;
    PaymentServiceStub paymentServiceStub;
    MessageStorage messageStorage;
    // todo move to config of order table
    private final boolean twoPhaseCommit = true;

    private static PaymentOuterClass.PaymentGetRequest paymentGetRequest(String paymentId) {
        return PaymentOuterClass.PaymentGetRequest.newBuilder()
                .setId(paymentId)
                .build();
    }

    private Mono<OrderApproveResponse> approveIfEnoughBalance(Order order, double balance) {
        return toMono(paymentGetRequest(order.paymentId()),
                paymentServiceStub::get).map(response -> response.getPayment().getAmount())
                .flatMap(paymentAmount -> {
                    if (paymentAmount < balance) {
                        return ordersService.approve(order.id(), twoPhaseCommit);
                    } else {
                        log.info(
                                "insufficient balance for order: orderId [{}], need money [{}], actual balance [{}]",
                                order.id(),
                                paymentAmount,
                                balance);
                        return empty();
                    }
                });
    }

    @PostConstruct
    public void consumeRecord() {
        reactiveKafkaConsumerTemplate.receiveAutoAck().map(r -> {
            var key = r.key();
            AccountBalanceEvent value = r.value();
            log.info("received account balance event from kafka consumer: key [{}], value: [{}]", key, value);
            return value;
        }).doOnError(error -> {
            log.error("receive account balance event error", error);
        }).flatMap(this::handle).doOnError(error -> {
            log.error("handle account balance event error", error);
        }).subscribe();
    }

    private Flux<OrderApproveResponse> handle(AccountBalanceEvent event) {
        var requestId = event.requestId();
        var clientId = event.clientId();
        var balance = event.balance();
        return messageStorage.storeUnique(MessageImpl.builder()
                .messageID(requestId)
                .subscriberID("accountBalance")
                .build())
                .thenMany(orderStorage.findByClientIdAndStatuses(clientId, Set.of(insufficient))
                        .doOnSuccess(orders -> {
                            if (log.isDebugEnabled()) {
                                log.debug("found active orders for client {}, amount {}, ids {}",
                                        clientId,
                                        orders.size(),
                                        orders.stream()
                                                .map(Order::id)
                                                .toList());
                            }
                        })
                        .flatMapMany(Flux::fromIterable)
                        .flatMap(order -> approveIfEnoughBalance(order, balance))
                        .doOnNext(response -> {
                            log.info("success order approve on account balance event: id [{}], status [{}]",
                                    response.getId(),
                                    response.getStatus());
                        })
                        .onErrorContinue((error, response) -> {
                            log.error("approve order on account balance event error", error);
                        }));
    }
}
