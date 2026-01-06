package io.github.m4gshm.reserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator.builder;

@SpringBootApplication
public class ReserveApplication {
    public static void main(String[] args) {
        // enable Virtual threads on boundedElastic()
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        // early hook registering
        builder().build().registerOnEachOperator();
        SpringApplication.run(ReserveApplication.class, args);
    }
}
