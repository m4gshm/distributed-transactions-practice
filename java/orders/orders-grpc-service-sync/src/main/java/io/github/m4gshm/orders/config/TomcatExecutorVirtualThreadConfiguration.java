package io.github.m4gshm.orders.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.springframework.boot.thread.Threading.VIRTUAL;

@Slf4j
@Configuration
@ConditionalOnThreading(VIRTUAL)
@ConditionalOnClass(TomcatProtocolHandlerCustomizer.class)
public class TomcatExecutorVirtualThreadConfiguration {

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(newVirtualThreadPerTaskExecutor());
        };
    }

}
