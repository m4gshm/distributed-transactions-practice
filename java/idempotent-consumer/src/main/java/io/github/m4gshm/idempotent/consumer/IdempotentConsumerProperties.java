package io.github.m4gshm.idempotent.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("idempotent-consumer")
public record IdempotentConsumerProperties(
                                           @DefaultValue("true") boolean createTable,
                                           @DefaultValue("true") boolean createPartition,
                                           @DefaultValue("true") boolean createPartitionOnInputMessage,
                                           @DefaultValue("yyyy_MM_dd") String partitionSuffixPattern
) {
}
