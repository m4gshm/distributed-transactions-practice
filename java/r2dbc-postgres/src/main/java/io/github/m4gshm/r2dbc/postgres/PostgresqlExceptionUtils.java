package io.github.m4gshm.r2dbc.postgres;

import io.r2dbc.postgresql.api.PostgresqlException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PostgresqlExceptionUtils {

    public static PostgresqlException getPostgresqlException(Throwable e) {
        if (e instanceof PostgresqlException postgresqlException) {
            return postgresqlException;
        } else if (e != null) {
            var cause = e.getCause();
            return cause == null || e == cause ? null : getPostgresqlException(cause);
        } else {
            return null;
        }
    }

}
