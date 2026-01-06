package io.github.m4gshm.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // (exclude = GrpcServerAutoConfiguration.class)
//@EnableConfigurationProperties({ GrpcTranscodingProperties.class, GrpcServerProperties.class })
public class OrdersSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrdersSyncApplication.class, args);
    }
}
