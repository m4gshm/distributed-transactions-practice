package io.github.m4gshm.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static io.github.m4gshm.VirtualThreadSchedulerUtils.initVirtualThreadScheduler;

@SpringBootApplication
public class PaymentsSyncApplication {
    public static void main(String[] args) {
        initVirtualThreadScheduler();
        SpringApplication.run(PaymentsSyncApplication.class, args);
    }
}
