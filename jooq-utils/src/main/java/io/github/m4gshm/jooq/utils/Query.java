package io.github.m4gshm.jooq.utils;

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
import static org.jooq.impl.DSL.field;
import static reactor.core.publisher.Mono.from;
import static reactor.core.publisher.Mono.justOrEmpty;

@Slf4j
@UtilityClass
public class Query {

    public static final Field<String> PG_CURRENT_XACT_ID = field("pg_current_xact_id()::text", String.class);
    public static final Field<String> PG_CURRENT_XACT_ID_IF_ASSIGNED = field("pg_current_xact_id_if_assigned()::text", String.class);

    public static Mono<String> getCurrentTxid(DSLContext dsl) {
        return select(dsl, PG_CURRENT_XACT_ID);
    }

    public static Mono<String> getCurrentTxidOrNull(DSLContext dsl, String noTxid) {
        return select(dsl, PG_CURRENT_XACT_ID_IF_ASSIGNED).defaultIfEmpty(noTxid);
    }

    public static Mono<String> select(DSLContext dsl, Field<String> field) {
        return from(dsl.select(field)).flatMap(r -> justOrEmpty(r.get(field)));
    }

    public static <R extends Record> SelectJoinStep<Record> selectAllFrom(DSLContext dsl, TableLike<R> table) {
        return dsl.select(table.fields()).from(table);
    }

    public static SelectSelectStep<Record> selectAll(DSLContext dsl, TableLike<? extends Record> table,
                                                     TableLike<? extends Record>... tables) {
        var fields = concat(Stream.of(table), stream(tables)).map(Fields::fields).flatMap(Arrays::stream).toList();
        return dsl.select(fields);
    }

    public static Mono<String> debugTxid(DSLContext dsl, String label) {
        return getCurrentTxidOrNull(dsl, "notxid").doOnSuccess(txid -> {
            log.debug("{} with txid {}", label, txid);
        });
    }

    public static <T> Mono<T> logTxId(DSLContext dsl, String label, T result) {
        return debugTxid(dsl, label).map(id -> result);
    }
}
