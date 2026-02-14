package io.github.m4gshm.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentsSyncApplication {
    public static void main(String[] args) {
        System.setProperty("jdk.pollerMode", "VTHREAD_POLLERS");
        SpringApplication.run(PaymentsSyncApplication.class, args);
    }
}
