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

import io.github.m4gshm.reactive.idempotent.consumer.MessageMaintenanceR2dbc;
import io.github.m4gshm.reactive.idempotent.consumer.MessageStorage;
import io.github.m4gshm.reactive.idempotent.consumer.MessageStorageR2dbc;
import io.github.m4gshm.utils.Jooq;
import io.github.m4gshm.utils.config.R2DBCJooqAutoConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@ConditionalOnBean(Jooq.class)
@FieldDefaults(makeFinal = true, level = PRIVATE)
@AutoConfiguration(after = R2DBCJooqAutoConfiguration.class)
@EnableConfigurationProperties(MessageStorageR2dbcImplAutoConfiguration.Properties.class)
public class MessageStorageR2dbcImplAutoConfiguration {

    Jooq jooq;
    Properties properties;

    @Bean
    @ConditionalOnMissingBean
    public MessageStorage messageStorageJooqR2dbcImpl() {
        var maintenanceService = new MessageMaintenanceR2dbc(jooq::inTransaction, INPUT_MESSAGES, true, "yyyy_MM_dd");
        return new MessageStorageR2dbc(maintenanceService,
                jooq::inTransaction,
                INPUT_MESSAGES,
                Clock.systemDefaultZone(),
                properties.createTable);
    }

    @ConfigurationProperties("idempotent-consumer")
    public record Properties(@DefaultValue("true") boolean createTable,
                             @DefaultValue("true") boolean initCurrentPartition,
                             @DefaultValue("true") boolean initNextPartition
    ) {
    }
}
