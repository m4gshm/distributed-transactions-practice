package io.github.m4gshm.idempotent.consumer.config;

import io.github.m4gshm.idempotent.consumer.IdempotentConsumerProperties;
import io.github.m4gshm.idempotent.consumer.MessageStorage;
import io.github.m4gshm.idempotent.consumer.MessageStorageImpl;
import io.github.m4gshm.idempotent.consumer.MessageStorageMaintenanceService;
import io.github.m4gshm.idempotent.consumer.MessageStorageMaintenanceServiceImpl;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

import static io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages.INPUT_MESSAGES;

@EnableScheduling
@RequiredArgsConstructor
@AutoConfiguration(after = { IdempotentConsumerPropertiesAutoConfiguration.class, JooqAutoConfiguration.class })
public class MessageStorageAutoConfiguration {

    private final IdempotentConsumerProperties properties;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DSLContext.class)
    public MessageStorage messageStorage(DSLContext dslContext) {
        return new MessageStorageImpl(
                messageStorageMaintenanceService(dslContext),
                dslContext,
                INPUT_MESSAGES,
                Clock.systemDefaultZone(),
                properties.createTable(),
                properties.createPartition(),
                properties.createPartitionOnInputMessage()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DSLContext.class)
    public MessageStorageMaintenanceService messageStorageMaintenanceService(DSLContext dslContext) {
        return new MessageStorageMaintenanceServiceImpl(
                dslContext,
                INPUT_MESSAGES,
                properties.partitionSuffixPattern());
    }

}
