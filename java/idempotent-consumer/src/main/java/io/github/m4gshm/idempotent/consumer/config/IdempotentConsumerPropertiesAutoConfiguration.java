package io.github.m4gshm.idempotent.consumer.config;

import io.github.m4gshm.idempotent.consumer.IdempotentConsumerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(IdempotentConsumerProperties.class)
public class IdempotentConsumerPropertiesAutoConfiguration {
}
