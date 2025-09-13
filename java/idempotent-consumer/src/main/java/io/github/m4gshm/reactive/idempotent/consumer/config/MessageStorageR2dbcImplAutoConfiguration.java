package io.github.m4gshm.reactive.idempotent.consumer.config;

import static io.github.m4gshm.reactive.idempotent.consumer.storage.tables.InputMessages.INPUT_MESSAGES;
import static lombok.AccessLevel.PRIVATE;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.config.R2DBCJooqAutoConfiguration;
import io.github.m4gshm.reactive.idempotent.consumer.MessageStorage;
import io.github.m4gshm.reactive.idempotent.consumer.MessageStorageMaintenanceR2Dbc;
import io.github.m4gshm.reactive.idempotent.consumer.MessageStorageMaintenanceService;
import io.github.m4gshm.reactive.idempotent.consumer.MessageStorageR2dbc;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnBean(Jooq.class)
@FieldDefaults(makeFinal = true, level = PRIVATE)
@AutoConfiguration(after = R2DBCJooqAutoConfiguration.class)
@EnableConfigurationProperties(MessageStorageR2dbcImplAutoConfiguration.Properties.class)
public class MessageStorageR2dbcImplAutoConfiguration {

    Jooq jooq;
    Properties properties;

    @ConfigurationProperties("idempotent-consumer")
    public record Properties(
                             @DefaultValue("true") boolean createTable,
                             @DefaultValue("true") boolean createPartition,
                             @DefaultValue("true") boolean createPartitionOnInputMessage,
                             @DefaultValue("yyyy_MM_dd") String partitionSuffixPattern
    ) {
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageStorage messageStorage() {
        var maintenanceService = messageStorageMaintenanceService();
        return new MessageStorageR2dbc(
                maintenanceService,
                jooq::inTransaction,
                INPUT_MESSAGES,
                Clock.systemDefaultZone(),
                properties.createTable,
                properties.createPartition,
                properties.createPartitionOnInputMessage
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageStorageMaintenanceService messageStorageMaintenanceService() {
        return new MessageStorageMaintenanceR2Dbc(
                jooq::inTransaction,
                INPUT_MESSAGES,
                properties.partitionSuffixPattern);
    }
}
