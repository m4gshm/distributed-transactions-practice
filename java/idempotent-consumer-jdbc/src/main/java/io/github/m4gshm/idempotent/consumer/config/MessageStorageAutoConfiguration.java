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
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages.INPUT_MESSAGES;

@EnableScheduling
@RequiredArgsConstructor
@AutoConfiguration(after = { IdempotentConsumerPropertiesAutoConfiguration.class,
        JooqAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class })
public class MessageStorageAutoConfiguration {

    private final IdempotentConsumerProperties properties;

    private static Clock getClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DSLContext.class)
    public MessageStorage messageStorage(DSLContext dslContext, MessageStorageMaintenanceService maintenanceService) {
        return new MessageStorageImpl(
                maintenanceService,
                dslContext,
                INPUT_MESSAGES,
                getClock(),
                properties.createPartitionOnInputMessage()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DSLContext.class)
    public MessageStorageMaintenanceService messageStorageMaintenanceService(
                                                                             DSLContext dslContext,
                                                                             PlatformTransactionManager transactionManager) {
        return new MessageStorageMaintenanceServiceImpl(
                dslContext,
                new TransactionTemplate(transactionManager),
                INPUT_MESSAGES,
                properties.partitionSuffixPattern(),
                getClock(),
                properties.createTable(),
                properties.createPartition());
    }

}
