package io.github.m4gshm.orders.service.event;

import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.ReactiveOrderStorage;
import io.github.m4gshm.orders.service.OrderService;
import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import io.github.m4gshm.idempotent.consumer.MessageImpl;
import io.github.m4gshm.idempotent.consumer.ReactiveMessageStorage;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.OrderServiceOuterClass.OrderApproveResponse;
import org.springframework.stereotype.Service;
import payment.v1.PaymentServiceGrpc.PaymentServiceStub;
import payment.v1.PaymentServiceOuterClass.PaymentGetRequest;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverOptions;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.Set;

import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.INSUFFICIENT;
import static io.github.m4gshm.reactive.ReactiveUtils.toMono;
import static lombok.AccessLevel.PRIVATE;
import static reactor.core.publisher.Mono.empty;
import static reactor.kafka.receiver.KafkaReceiver.create;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE)
public class KafkaAccountBalanceEventListenerServiceImpl {
    final ReceiverOptions<String, AccountBalanceEvent> balanceReceiverOptions;
    final ReactiveOrderStorage orderStorage;
    final OrderService ordersService;
    final PaymentServiceStub paymentServiceStub;
    final ReactiveMessageStorage reactiveMessageStorage;
    // todo move to config of order table
    final boolean twoPhaseCommit = true;
    final ObservationRegistry observationRegistry;

    volatile Disposable subscribe;

    private static PaymentGetRequest paymentGetRequest(String paymentId) {
        return PaymentGetRequest.newBuilder()
                .setId(paymentId)
                .build();
    }

    private Mono<OrderApproveResponse> approveIfEnoughBalance(Order order, double balance) {
        return toMono("paymentService::get",
                paymentGetRequest(order.paymentId()),
                paymentServiceStub::get)
                .map(response -> response.getPayment().getAmount())
                .flatMap(paymentAmount -> {
                    if (paymentAmount < balance) {
                        return ordersService.approve(order.id(), twoPhaseCommit);
                    } else {
                        log.info("insufficient balance for order: orderId [{}], need money [{}], actual balance [{}]",
                                order.id(),
                                paymentAmount,
                                balance);
                        return empty();
                    }
                });
    }

    @PostConstruct
    public void consumeRecord() {
        var kafkaReceiver = create(balanceReceiverOptions);
        var receive = kafkaReceiver.receive();
        subscribe = receive.map(record -> {
            var offset = record.receiverOffset();
            var timestamp = Instant.ofEpochMilli(record.timestamp());
            var key = record.key();
            AccountBalanceEvent value = record.value();
            log.info("received account balance event from kafka consumer: key {}, value {}, offset {}, timestamp {} ",
                    key,
                    value,
                    offset,
                    timestamp);
            offset.acknowledge();
            return value;
        }).doOnError(error -> {
            log.error("receive account balance event error", error);
        }).flatMap(this::handle).doOnError(error -> {
            log.error("handle account balance event error", error);
        }).subscribe();
    }

    @PreDestroy
    public void destroy() {
        var s = subscribe;
        if (s != null && !s.isDisposed()) {
            s.dispose();
        }
    }

    private Flux<OrderApproveResponse> handle(AccountBalanceEvent event) {
        var requestId = event.requestId();
        var clientId = event.clientId();
        var balance = event.balance();
        return reactiveMessageStorage.storeUnique(MessageImpl.builder()
                .messageID(requestId)
                .subscriberID("accountBalance")
                .timestamp(event.timestamp())
                .build())
                .thenMany(orderStorage.findByClientIdAndStatuses(clientId, Set.of(INSUFFICIENT))
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
