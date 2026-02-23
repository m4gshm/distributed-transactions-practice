package io.github.m4gshm.storage.jooq;

import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Fields;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.TableLike;

import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

@UtilityClass
public class Query {

    public static String select(DSLContext dsl, Field<String> field) {
        return dsl.select(field).stream().map(r -> r.get(field)).findAny().orElse(null);
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
