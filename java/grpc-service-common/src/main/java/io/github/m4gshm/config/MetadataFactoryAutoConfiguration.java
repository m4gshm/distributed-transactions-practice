package io.github.m4gshm.config;

import io.github.m4gshm.MetadataFactory;
import io.github.m4gshm.MetadataFactoryImpl;
import io.github.m4gshm.StatusExtractor;
import io.github.m4gshm.StatusExtractorImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class MetadataFactoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetadataFactory metadataFactory() {
        return new MetadataFactoryImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public StatusExtractor statusExtractor() {
        return new StatusExtractorImpl();
    }
}
