package io.github.m4gshm.storage.jooq;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import reactor.core.publisher.Mono;

import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.justOrEmpty;

@Slf4j
@UtilityClass
public class ReactiveQueryUtils {

    public static Mono<String> select(DSLContext dsl, Field<String> field) {
        return from(dsl.select(field)).flatMap(r -> justOrEmpty(r.get(field)));
    }

}
