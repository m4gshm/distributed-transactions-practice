package io.github.m4gshm.protobuf;

import java.util.function.Function;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {

    public static <T, V> V getOrNull(T request, Predicate<T> has, Function<T, V> get) {
        return has.test(request) ? get.apply(request) : null;
    }

}
