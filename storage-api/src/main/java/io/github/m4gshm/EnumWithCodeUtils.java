package io.github.m4gshm;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@UtilityClass
public class EnumWithCodeUtils {

    private static final Map<Class<?>, Map<String, Object>> enums = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T> & EnumWithCode<T>> T getByCode(Class<T> type, String code) {
        return (T) enums.computeIfAbsent(type, _ -> stream(type.getEnumConstants())
                .collect(toMap(EnumWithCode::getCode, identity()))).get(code);
    }

    public static <T extends EnumWithCode<?>> String getCode(T value) {
        return ofNullable(value).map(EnumWithCode::getCode).orElse(null);
    }
}
