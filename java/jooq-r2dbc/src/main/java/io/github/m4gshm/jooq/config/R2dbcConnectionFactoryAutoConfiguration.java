package io.github.m4gshm.jooq.config;

import io.r2dbc.spi.ConnectionFactory;
import org.jooq.Configuration;
import org.jooq.SubscriberProvider;
import org.jooq.conf.Settings;
import org.jooq.impl.DefaultConfiguration;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import java.util.function.Consumer;

import static org.jooq.conf.ParamType.INLINED;
import static org.jooq.conf.StatementType.STATIC_STATEMENT;
import static org.jooq.tools.jdbc.JDBCUtils.dialect;

@AutoConfiguration(before = { DSLContextAutoConfiguration.class, JooqAutoConfiguration.class })
public class R2dbcConnectionFactoryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(Configuration.class)
    Configuration jooqConfiguration(ConnectionFactory connectionFactory) {
        var tConnectionFactory = new TransactionAwareConnectionFactoryProxy(connectionFactory);
        var configuration = new DefaultConfiguration();
        configuration.set(dialect(connectionFactory));
        configuration.set(tConnectionFactory);
        configuration.set(new Settings()
                        .withRenderSchema(false)
//                .withParamType(INLINED)
//                .withStatementType(STATIC_STATEMENT)
        );
        configuration.set(new SubscriberProvider<>() {

            @Override
            public Object context() {
                return Context.empty();
            }

            @Override
            public Object context(Subscriber<?> subscriber) {
                if (subscriber instanceof CoreSubscriber<?> coreSubscriber) {
                    return coreSubscriber.currentContext();
                }
                // log
                return context();
            }

            @Override
            public <T> Subscriber<T> subscriber(Consumer<? super Subscription> onSubscribe,
                                                Consumer<? super T> onNext,
                                                Consumer<? super Throwable> onError,
                                                Runnable onComplete,
                                                Object context) {
                return new CoreSubscriber<T>() {
                    @Override
                    public Context currentContext() {
                        if (context instanceof Context rc) {
                            return rc;
                        } else {
                            // log
                            return Context.empty();
                        }
                    }

                    @Override
                    public void onComplete() {
                        onComplete.run();
                    }

                    @Override
                    public void onError(Throwable t) {
                        onError.accept(t);
                    }

                    @Override
                    public void onNext(T t) {
                        onNext.accept(t);
                    }

                    @Override
                    public void onSubscribe(Subscription s) {
                        onSubscribe.accept(s);
                    }
                };
            }
        });
        return configuration;
    }

}
