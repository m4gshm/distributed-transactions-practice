package io.github.m4gshm.jooq.config;

import io.github.m4gshm.jooq.R2dbcSubscriberProvider;
import io.r2dbc.spi.ConnectionFactory;
import org.jooq.Configuration;
import org.jooq.ExecuteListenerProvider;
import org.jooq.conf.Settings;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;

import static org.jooq.tools.jdbc.JDBCUtils.dialect;

@AutoConfiguration(before = { DSLContextAutoConfiguration.class,
        JooqAutoConfiguration.class }, after = DefaultConfigurationCustomizerAutoConfiguration.class)
public class R2dbcConnectionFactoryAutoConfiguration {

//    @Bean
//    public BeanPostProcessor transactionAwareConnectionFactoryProxyPostProcessor() {
//        return new BeanPostProcessor() {
//            @Override
//            public Object postProcessAfterInitialization(Object bean, String beanName) {
//                if (bean instanceof ConnectionFactory connectionFactory) {
//                    if (!(bean instanceof TransactionAwareConnectionFactoryProxy)) {
//                        return new TransactionAwareConnectionFactoryProxy(connectionFactory);
//                    }
//                }
//                return bean;
//            }
//        };
//    }

    @Bean
    @ConditionalOnMissingBean(Configuration.class)
    Configuration jooqConfiguration(ConnectionFactory connectionFactory,
                                    ObjectProvider<ExecuteListenerProvider> executeListenerProviders,
                                    ObjectProvider<DefaultConfigurationCustomizer> configurationCustomizers,
                                    ObjectProvider<Settings> settingsProvider) {
        var r2dbcSubscriberProvider = new R2dbcSubscriberProvider();

        var configuration = new DefaultConfiguration();
        configuration.set(dialect(connectionFactory));
//        configuration.set(connectionFactory instanceof TransactionAwareConnectionFactoryProxy t
//                ? t
//                : new TransactionAwareConnectionFactoryProxy(connectionFactory));
        configuration.set(connectionFactory);
        configuration.set(r2dbcSubscriberProvider);

        settingsProvider.ifAvailable(configuration::set);
        configuration.set(executeListenerProviders.orderedStream().toArray(ExecuteListenerProvider[]::new));
        configurationCustomizers.orderedStream().forEach((customizer) -> customizer.customize(configuration));

        return configuration;
    }

}
