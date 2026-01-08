package io.github.m4gshm.orders.service.event;

import io.github.m4gshm.idempotent.consumer.MessageImpl;
import io.github.m4gshm.idempotent.consumer.MessageStorage;
import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.service.OrderService;
import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import payment.v1.PaymentServiceGrpc;
import payment.v1.PaymentServiceOuterClass.PaymentGetRequest;

import java.util.Set;

import static io.github.m4gshm.orders.data.access.jooq.enums.OrderStatus.INSUFFICIENT;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KafkaAccountBalanceEventListenerServiceImpl {
    final OrderStorage orderStorage;
    final OrderService ordersService;
    final PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;
    final MessageStorage messageStorage;
    // todo move to config of order table
    final boolean twoPhaseCommit = false;
    final ObservationRegistry observationRegistry;

    private static PaymentGetRequest paymentGetRequest(String paymentId) {
        return PaymentGetRequest.newBuilder()
                .setId(paymentId)
                .build();
    }

    private void approveIfEnoughBalance1(Order order, double balance) {
        var paymentGetResponse = paymentServiceStub.get(paymentGetRequest(order.paymentId()));
        var payment = paymentGetResponse.getPayment();
        var paymentAmount = payment.getAmount();
        if (paymentAmount <= balance) {
            var response = ordersService.approve(order.id(), twoPhaseCommit);
            log.info("success order approve on account balance event: id [{}], status [{}]",
                    response.getId(),
                    response.getStatus());
        }
    }

    private void handle(AccountBalanceEvent event) {
        var requestId = event.requestId();
        var clientId = event.clientId();
        var balance = event.balance();
        messageStorage.storeUnique(MessageImpl.builder()
                .messageID(requestId)
                .subscriberID("accountBalance")
                .timestamp(event.timestamp())
                .build());

        var orders = orderStorage.findByClientIdAndStatuses(clientId, Set.of(INSUFFICIENT));

        if (log.isDebugEnabled()) {
            log.debug("found active orders for client {}, amount {}, ids {}",
                    clientId,
                    orders.size(),
                    orders.stream()
                            .map(Order::id)
                            .toList());
        }

        for (var order : orders) {
            try {
                approveIfEnoughBalance1(order, balance);
            } catch (Exception e) {
                log.error("approve order on account balance event error", e);
            }
        }
    }

    @KafkaListener(topics = "balance", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(AccountBalanceEvent value) {
        log.info("received account balance event from kafka consumer: value {}", value);
        handle(value);
    }
}
