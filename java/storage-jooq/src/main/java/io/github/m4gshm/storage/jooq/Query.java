package io.github.m4gshm.storage.jooq;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Fields;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.TableLike;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.justOrEmpty;

@Slf4j
@UtilityClass
public class Query {

    public static Mono<String> select(DSLContext dsl, Field<String> field) {
        return from(dsl.select(field)).flatMap(r -> justOrEmpty(r.get(field)));
    }

    public static SelectSelectStep<Record> selectAll(DSLContext dsl,
                                                     TableLike<? extends Record> table,
                                                     TableLike<? extends Record>... tables) {
        var fields = concat(Stream.of(table), stream(tables)).map(Fields::fields).flatMap(Arrays::stream).toList();
        return dsl.select(fields);
    }

    public static <R extends Record> SelectJoinStep<Record> selectAllFrom(DSLContext dsl, TableLike<R> table) {
        return dsl.select(table.fields()).from(table);
    }

}
