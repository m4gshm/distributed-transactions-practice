package io.github.m4gshm.reactive.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.netty.resources.LoopResources;

@AutoConfiguration
public class LoopResourcesConfiguration {

    @Bean
    public LoopResources sharedLoopResources() {
        return LoopResources.create("shared-netty-loops");
    }

}
