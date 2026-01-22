package io.github.m4gshm.storage;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

import static io.github.m4gshm.storage.NotFoundException.newNotFoundException;

@Slf4j
@UtilityClass
public class UpdateUtils {
    public static <ID, T> T checkUpdateCount(int count, String entity, ID id, Supplier<T> result) {
        log.debug("update result count: entity [{}], id [{}], rows [{}]", entity, id, count);
        if (count > 0) {
            return result.get();
        }
        throw notFound(entity, id);
    }

    public static NotFoundException notFound(String entity, Object id) {
        return newNotFoundException("zero updated count on " + entity + " " + id, entity, id);
    }
}
