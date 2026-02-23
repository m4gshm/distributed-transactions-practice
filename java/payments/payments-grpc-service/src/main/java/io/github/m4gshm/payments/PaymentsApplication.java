package io.github.m4gshm.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Hooks;

@EnableWebFlux
@SpringBootApplication
public class PaymentsApplication {
    public static void main(String[] args) {
        // enable Virtual threads on boundedElastic()
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(PaymentsApplication.class, args);
    }
}
