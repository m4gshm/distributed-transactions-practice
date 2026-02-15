package io.github.m4gshm.reserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static io.github.m4gshm.VirtualThreadSchedulerUtils.initVirtualThreadScheduler;

@SpringBootApplication
public class ReserveSyncApplication {
    public static void main(String[] args) {
        initVirtualThreadScheduler();
        SpringApplication.run(ReserveSyncApplication.class, args);
    }
}
