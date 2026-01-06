package io.github.m4gshm.storage;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import lombok.Getter;

@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    private final String entityType;
    private final Object[] keys;

    private static String message(String entityType, Object[] keys) {
        return entityType + " not found by " + Arrays.toString(keys);
    }

    public static NotFoundException newNotFoundException(Class<?> entityType, Object... keys) {
        return newNotFoundException(null, entityType, keys);
    }

    public static NotFoundException newNotFoundException(String message, String entityType, Object... keys) {
        return new NotFoundException(null, message, entityType, keys);
    }

    public static NotFoundException newNotFoundException(Throwable cause, Class<?> entityType, Object... keys) {
        var stringEntityType = stringEntityType(entityType);
        var message = message(stringEntityType, keys);
        return new NotFoundException(cause, message, stringEntityType, keys);
    }

    private static String stringEntityType(Class<?> entityType) {
        return entityType != null ? entityType.getSimpleName() : "";
    }

    public NotFoundException(Throwable cause, String message, String entityType, Object... keys) {
        super(message, cause);
        this.keys = keys;
        this.entityType = entityType;
    }
}
