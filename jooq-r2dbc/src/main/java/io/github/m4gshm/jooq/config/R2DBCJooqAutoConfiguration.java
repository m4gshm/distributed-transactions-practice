package io.github.m4gshm.jooq.config;

import io.github.m4gshm.jooq.Jooq;
import io.github.m4gshm.jooq.JooqImpl;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy;
import org.springframework.transaction.reactive.TransactionalOperator;

import static lombok.AccessLevel.PRIVATE;
import static org.jooq.conf.ParamType.INLINED;
import static org.jooq.conf.StatementType.STATIC_STATEMENT;
import static org.jooq.tools.jdbc.JDBCUtils.dialect;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class R2DBCJooqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(org.jooq.Configuration.class)
    DefaultConfiguration jooqConfiguration(ConnectionFactory connectionFactory) {
        var transactionAwareConnectionFactoryProxy = new TransactionAwareConnectionFactoryProxy(connectionFactory);
        var configuration = new DefaultConfiguration();
        configuration.set(dialect(transactionAwareConnectionFactoryProxy));
        configuration.set(transactionAwareConnectionFactoryProxy);
        configuration.set(new Settings().withParamType(INLINED).withStatementType(STATIC_STATEMENT));
        return configuration;
    }

    @Bean
    public DSLContext dslContext(Configuration configuration) {
        return DSL.using(configuration);
    }

    @Bean
    public Jooq jooq(TransactionalOperator operator, ConnectionFactory connectionFactory, Configuration configuration) {
        return new JooqImpl(operator, connectionFactory, configuration);
    }

}
