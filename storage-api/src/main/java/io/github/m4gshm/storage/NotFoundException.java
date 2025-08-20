package io.github.m4gshm.storage;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Arrays;

@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    private final Object[] keys;

    private static String message(Class<?> entityType, Object[] keys) {
        return message(entityType.getSimpleName(), keys);
    }

    private static String message(String entityType, Object[] keys) {
        return entityType + " not found by " + Arrays.toString(keys);
    }

    public NotFoundException(Class<?> entityType, Object... keys) {
        this(message(entityType.getName(), keys), keys);
    }

    public NotFoundException(String message, Object... keys) {
        super(message);
        this.keys = keys;
    }

    public NotFoundException(Throwable cause, Class<?> entityType, Object... keys) {
        super(message(entityType, keys), cause);
        this.keys = keys;
    }
}
