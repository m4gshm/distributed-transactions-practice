package io.github.m4gshm.orders.service.event;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE)
public class KafkaAccountBalanceEventListenerServiceImpl {
//    final ReceiverOptions<String, AccountBalanceEvent> balanceReceiverOptions;
//    final OrderStorage orderStorage;
//    final OrderService ordersService;
//    final PaymentServiceStub paymentServiceStub;
//    final MessageStorage messageStorage;
//    // todo move to config of order table
//    final boolean twoPhaseCommit = true;
//    final ObservationRegistry observationRegistry;
//
//    volatile Disposable subscribe;
//
//    private static PaymentGetRequest paymentGetRequest(String paymentId) {
//        return PaymentGetRequest.newBuilder()
//                .setId(paymentId)
//                .build();
//    }
//
//    private Mono<OrderApproveResponse> approveIfEnoughBalance(Order order, double balance) {
//        return toMono("paymentService::get",
//                paymentGetRequest(order.paymentId()),
//                paymentServiceStub::get)
//                .map(response -> response.getPayment().getAmount())
//                .flatMap(paymentAmount -> {
//                    if (paymentAmount < balance) {
//                        return ordersService.approve(order.id(), twoPhaseCommit);
//                    } else {
//                        log.info("insufficient balance for order: orderId [{}], need money [{}], actual balance [{}]",
//                                order.id(),
//                                paymentAmount,
//                                balance);
//                        return empty();
//                    }
//                });
//    }
//
//    @PostConstruct
//    public void consumeRecord() {
//        var kafkaReceiver = create(balanceReceiverOptions);
//        var receive = kafkaReceiver.receive();
//        subscribe = receive.map(record -> {
//            var offset = record.receiverOffset();
//            var timestamp = Instant.ofEpochMilli(record.timestamp());
//            var key = record.key();
//            AccountBalanceEvent value = record.value();
//            log.info("received account balance event from kafka consumer: key {}, value {}, offset {}, timestamp {} ",
//                    key,
//                    value,
//                    offset,
//                    timestamp);
//            offset.acknowledge();
//            return value;
//        }).doOnError(error -> {
//            log.error("receive account balance event error", error);
//        }).flatMap(this::handle).doOnError(error -> {
//            log.error("handle account balance event error", error);
//        }).subscribe();
//    }
//
//    @PreDestroy
//    public void destroy() {
//        var s = subscribe;
//        if (s != null && !s.isDisposed()) {
//            s.dispose();
//        }
//    }
//
//    private Flux<OrderApproveResponse> handle(AccountBalanceEvent event) {
//        var requestId = event.requestId();
//        var clientId = event.clientId();
//        var balance = event.balance();
//        return messageStorage.storeUnique(MessageImpl.builder()
//                .messageID(requestId)
//                .subscriberID("accountBalance")
//                .timestamp(event.timestamp())
//                .build())
//                .thenMany(orderStorage.findByClientIdAndStatuses(clientId, Set.of(INSUFFICIENT))
//                        .doOnSuccess(orders -> {
//                            if (log.isDebugEnabled()) {
//                                log.debug("found active orders for client {}, amount {}, ids {}",
//                                        clientId,
//                                        orders.size(),
//                                        orders.stream()
//                                                .map(Order::id)
//                                                .toList());
//                            }
//                        })
//                        .flatMapMany(Flux::fromIterable)
//                        .flatMap(order -> approveIfEnoughBalance(order, balance))
//                        .doOnNext(response -> {
//                            log.info("success order approve on account balance event: id [{}], status [{}]",
//                                    response.getId(),
//                                    response.getStatus());
//                        })
//                        .onErrorContinue((error, response) -> {
//                            log.error("approve order on account balance event error", error);
//                        }));
//    }
}
