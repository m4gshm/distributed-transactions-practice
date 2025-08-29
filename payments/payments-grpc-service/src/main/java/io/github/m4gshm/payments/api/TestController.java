package io.github.m4gshm.payments.api;

import io.github.m4gshm.payments.service.event.AccountEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final AccountEventService accountEventService;

    @PostMapping("/event/account/balance")
    public Mono<SenderResult<Void>> sendAccountBalanceEvent(@RequestBody AccountBalanceEvent body) {
        return accountEventService.sendAccountBalanceEvent(body.clientId, body.balance);
    }

    public record AccountBalanceEvent(String clientId, double balance) {

    }

}
