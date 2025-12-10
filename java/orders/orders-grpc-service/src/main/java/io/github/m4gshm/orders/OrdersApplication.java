package io.github.m4gshm.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator.builder;

@SpringBootApplication
public class OrdersApplication {
    public static void main(String[] args) {
        // early hook registering
        builder().build().registerOnEachOperator();
        SpringApplication.run(OrdersApplication.class, args);
    }
}
