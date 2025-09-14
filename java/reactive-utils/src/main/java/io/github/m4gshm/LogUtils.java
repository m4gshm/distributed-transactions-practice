package io.github.m4gshm;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

@UtilityClass
public class LogUtils {
    public static <T> Mono<T> log(Class<?> beanType, String category, Mono<T> mono) {
        return mono.log(loggerName(beanType, category), java.util.logging.Level.FINE);
    }

    public static String loggerName(Class<?> beanType, String category) {
        return beanType.getName() + "." + category;
    }
}
