package jooq.config;

import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jooq.DSLContext;
import org.jooq.conf.ParamType;
import org.jooq.conf.Settings;
import org.jooq.conf.StatementType;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.tools.jdbc.JDBCUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy;

import static lombok.AccessLevel.PRIVATE;
import static org.jooq.conf.ParamType.INLINED;
import static org.jooq.conf.StatementType.STATIC_STATEMENT;

@AutoConfiguration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class DSLContextConfiguration {

    @Bean
    public DSLContext dslContext(ConnectionFactory connectionFactory) {
        var transactionAwareConnectionFactoryProxy = new TransactionAwareConnectionFactoryProxy(connectionFactory);
        var dialect = JDBCUtils.dialect(transactionAwareConnectionFactoryProxy);
        var configuration = new DefaultConfiguration()
                //for debug only
                .set(new Settings().withParamType(INLINED).withStatementType(STATIC_STATEMENT))
                .set(transactionAwareConnectionFactoryProxy)
                .set(dialect);
        return DSL.using(configuration);
    }
}
