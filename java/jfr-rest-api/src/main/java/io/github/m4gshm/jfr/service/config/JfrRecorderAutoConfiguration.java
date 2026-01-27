package io.github.m4gshm.jfr.service.config;

import io.github.m4gshm.jfr.service.JfrRecorder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class JfrRecorderAutoConfiguration {
    @Bean
    public JfrRecorder jfrRecorder() {
        return new JfrRecorder();
    }
}
