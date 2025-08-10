package io.github.m4gshm.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class PaymentsApplication {
    public static void main(String[] args) {
        Hooks.onOperatorDebug();
        SpringApplication.run(PaymentsApplication.class, args);
    }
}
