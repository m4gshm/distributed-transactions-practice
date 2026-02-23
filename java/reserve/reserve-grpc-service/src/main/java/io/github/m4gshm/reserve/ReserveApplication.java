package io.github.m4gshm.reserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class ReserveApplication {
    public static void main(String[] args) {
        // enable Virtual threads on boundedElastic()
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(ReserveApplication.class, args);
    }
}
