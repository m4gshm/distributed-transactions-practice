package io.github.m4gshm.reserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReserveSyncApplication {
    public static void main(String[] args) {
        System.setProperty("jdk.pollerMode", "VTHREAD_POLLERS");
        SpringApplication.run(ReserveSyncApplication.class, args);
    }
}
