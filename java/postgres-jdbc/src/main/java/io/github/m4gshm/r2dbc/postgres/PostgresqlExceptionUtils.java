package io.github.m4gshm.r2dbc.postgres;

import lombok.experimental.UtilityClass;
import org.postgresql.util.PSQLException;

@UtilityClass
public class PostgresqlExceptionUtils {

    public static PSQLException getPostgresqlException(Throwable e) {
        if (e instanceof PSQLException postgresqlException) {
            return postgresqlException;
        } else if (e != null) {
            var cause = e.getCause();
            return cause == null || e == cause ? null : getPostgresqlException(cause);
        } else {
            return null;
        }
    }

}
