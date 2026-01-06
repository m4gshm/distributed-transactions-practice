package io.github.m4gshm.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrdersApplication {
    public static void main(String[] args) {
        // enable Virtual threads on boundedElastic()
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        // early hook registering
//        ContextPropagationOperator.builder().build().registerOnEachOperator();
//        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(OrdersApplication.class, args);
    }
}
