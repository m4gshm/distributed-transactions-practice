package io.github.m4gshm.payments.api;

import java.time.OffsetDateTime;

import org.springframework.web.bind.annotation.RestController;

import io.github.m4gshm.payments.service.event.AccountEventService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final AccountEventService accountEventService;

    public record AccountBalanceEvent(String clientId, double balance, OffsetDateTime timestamp) {

    }

//    @PostMapping("/event/account/balance")
//    public Mono<String> sendAccountBalanceEvent(@RequestBody AccountBalanceEvent body) {
//        return accountEventService.sendAccountBalanceEvent(body.clientId, body.balance, body.timestamp).flatMap(r -> {
//            var exception = r.exception();
//            return exception != null ? error(exception) : just(r.recordMetadata().toString());
//        });
//    }

}
