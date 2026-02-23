package io.github.m4gshm.orders;

import io.github.m4gshm.asyncprof.AsyncProfController;
import io.github.m4gshm.jfr.controller.JfrController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication(scanBasePackageClasses = {
        OrdersApplication.class,
        JfrController.class,
        AsyncProfController.class,
})
public class OrdersApplication {
    public static void main(String[] args) {
        // enable Virtual threads on boundedElastic()
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(OrdersApplication.class, args);
    }
}
