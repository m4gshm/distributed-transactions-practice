package io.github.m4gshm.r2dbc.postgres.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import reactor.netty.resources.LoopResources;

import static io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.LOOP_RESOURCES;
import static io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.TCP_KEEPALIVE;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(ConnectionFactoryOptionsBuilderCustomizer.class)
public class ConnectionFactoryOptionsBuilderCustomizerSharedLoopAutoConfiguration {
    @Bean
//    @ConditionalOnBean(LoopResources.class)
    public ConnectionFactoryOptionsBuilderCustomizer connectionFactoryOptionsBuilderCustomizerSharedLoopResources(
                                                                                                                  ObjectProvider<
                                                                                                                          LoopResources> sharedLoopResources
    ) {
        var loopResources = sharedLoopResources.getIfAvailable();
        return builder -> {
            if (loopResources != null) {
                builder.option(LOOP_RESOURCES, loopResources);
                builder.option(TCP_KEEPALIVE, true);
            } else {
                log.info("ConnectionFactoryOptionsBuilder no customized by shared LoopResources");
            }
        };
    }
}
