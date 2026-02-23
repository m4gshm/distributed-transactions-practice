package io.m4gshm.dtp.orders.sqlc.config

import io.github.m4gshm.orders.data.storage.OrderStorage
import io.github.m4gshm.orders.data.storage.jdbc.config.OrderStorageImplAutoConfiguration
import io.m4gshm.dtp.orders.sqlc.OrderStorageSqlcImpl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@AutoConfiguration(before = [OrderStorageImplAutoConfiguration::class])
class OrderStorageSqlcImplAutoConfiguration {
    @Bean
    @Primary
    @ConditionalOnProperty("sqlc.enabled", havingValue = "true")
    fun orderStorage(dataSource: DataSource): OrderStorage {
        return OrderStorageSqlcImpl(dataSource)
    }
}
