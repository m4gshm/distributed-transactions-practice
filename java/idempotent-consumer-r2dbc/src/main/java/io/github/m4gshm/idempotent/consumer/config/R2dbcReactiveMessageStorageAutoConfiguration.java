package io.github.m4gshm.idempotent.consumer.config;

import io.github.m4gshm.jooq.ReactiveJooq;
import io.github.m4gshm.idempotent.consumer.IdempotentConsumerProperties;
import io.github.m4gshm.idempotent.consumer.R2dbcReactiveMessageStorage;
import io.github.m4gshm.idempotent.consumer.R2dbcReactiveMessageStorageMaintenanceService;
import io.github.m4gshm.idempotent.consumer.ReactiveMessageStorage;
import io.github.m4gshm.idempotent.consumer.ReactiveMessageStorageMaintenanceService;
import io.github.m4gshm.jooq.config.R2dbcReactiveJooqAutoConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

import static io.github.m4gshm.idempotent.consumer.storage.tables.InputMessages.INPUT_MESSAGES;

@EnableScheduling
@RequiredArgsConstructor

@AutoConfiguration(after = R2dbcReactiveJooqAutoConfiguration.class)
public class R2dbcReactiveMessageStorageAutoConfiguration {

    private final IdempotentConsumerProperties properties;

    @Bean
//    @ConditionalOnMissingBean
    @ConditionalOnBean(ReactiveJooq.class)
    public ReactiveMessageStorage reactiveMessageStorage(
                                                         ReactiveMessageStorageMaintenanceService reactiveMessageStorageMaintenanceService,
                                                         ReactiveJooq jooq
    ) {
        return new R2dbcReactiveMessageStorage(
                reactiveMessageStorageMaintenanceService,
                jooq::inTransaction,
                Clock.systemDefaultZone(),
                properties.createTable(),
                properties.createPartition(),
                properties.createPartitionOnInputMessage()
        );
    }

    @Bean
//    @ConditionalOnMissingBean
    @ConditionalOnBean(ReactiveJooq.class)
    public ReactiveMessageStorageMaintenanceService reactiveMessageStorageMaintenanceService(ReactiveJooq jooq) {
        return new R2dbcReactiveMessageStorageMaintenanceService(
                jooq::inTransaction,
                INPUT_MESSAGES,
                properties.partitionSuffixPattern());
    }

}
