package io.github.m4gshm.orders.service.event;

import io.github.m4gshm.orders.data.model.Order;
import io.github.m4gshm.orders.data.storage.OrderStorage;
import io.github.m4gshm.orders.service.OrdersService;
import io.github.m4gshm.payments.event.model.AccountBalanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.v1.Orders.OrderApproveResponse;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.Set;

import static io.github.m4gshm.orders.data.model.Order.Status.insufficient;
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
    //todo move to config of order table
    private final boolean twoPhaseCommit = true;

    @PostConstruct
    public void consumeRecord() {
        reactiveKafkaConsumerTemplate.receiveAutoAck().map(r -> {
            var key = r.key();
            AccountBalanceEvent value = r.value();
            log.info("received account balance event from kafka consumer: {}, value: {}", key, value);
            return value;
        }).doOnError(error -> {
            log.error("account balance event error", error);
        }).flatMap(this::handle).subscribe();
    }

    private Flux<OrderApproveResponse> handle(AccountBalanceEvent event) {
        var clientId = event.clientId();
        var balance = event.balance();
        return orderStorage.findByClientIdAndStatuses(clientId, Set.of(insufficient)
        ).doOnSuccess(orders -> {
            if (log.isDebugEnabled()) {
                log.debug("found active orders for client {}, amount {}, ids {}",
                        clientId, orders.size(), orders.stream().map(Order::id).toList());
            }
        }).flatMapMany(Flux::fromIterable).flatMap(order -> {
            var sumAmount = order.items().stream().mapToDouble(Order.Item::amount).sum();
            if (sumAmount > balance) {
                return ordersService.approve(order.id(), twoPhaseCommit);
            } else {
                log.info("insufficient balance for order approving: orderId [{}], need money [{}], actual balance [{}]",
                        order.id(), sumAmount, balance);
                return empty();
            }
        }).doOnNext(response -> {
            log.info("success order approve on account balance event: id [{}], status [{}]",
                    response.getId(), response.getStatus());
        }).onErrorContinue((error, response) -> {
            log.error("approve order on account balance event error", error);
        });
    }
}
