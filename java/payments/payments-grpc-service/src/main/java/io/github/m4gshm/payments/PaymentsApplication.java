package io.github.m4gshm.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;

import static io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator.builder;

@EnableWebFlux
@SpringBootApplication
public class PaymentsApplication {
    public static void main(String[] args) {
        // early hook registering
        builder().build().registerOnEachOperator();
        SpringApplication.run(PaymentsApplication.class, args);
    }
}
