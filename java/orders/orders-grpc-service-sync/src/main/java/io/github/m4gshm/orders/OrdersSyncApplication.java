package io.github.m4gshm.orders;

import io.github.m4gshm.asyncprof.AsyncProfController;
import io.github.m4gshm.jfr.controller.JfrController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackageClasses = {
        OrdersSyncApplication.class,
        JfrController.class,
        AsyncProfController.class,
})
public class OrdersSyncApplication {
    public static void main(String[] args) {
        System.setProperty("jdk.pollerMode", "VTHREAD_POLLERS");
        SpringApplication.run(OrdersSyncApplication.class, args);
    }
}
