package io.github.m4gshm.postgres.prepared.transaction;

import io.github.m4gshm.storage.jooq.Query;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import reactor.core.publisher.Mono;

import static org.jooq.impl.DSL.field;

@Slf4j
@UtilityClass
public class Transaction {
    public static final Field<String> PG_CURRENT_XACT_ID = field("pg_current_xact_id()::text", String.class);
    public static final Field<String> PG_CURRENT_XACT_ID_IF_ASSIGNED = field("pg_current_xact_id_if_assigned()::text",
            String.class);

    public static Mono<String> debugTxid(DSLContext dsl, String label) {
        return getCurrentTxidOrNull(dsl, "notxid").doOnSuccess(txid -> {
            log.debug("{} with txid {}", label, txid);
        });
    }

    public static Mono<String> getCurrentTxid(DSLContext dsl) {
        return Query.select(dsl, PG_CURRENT_XACT_ID);
    }

    public static Mono<String> getCurrentTxidOrNull(DSLContext dsl, String noTxid) {
        return Query.select(dsl, PG_CURRENT_XACT_ID_IF_ASSIGNED).defaultIfEmpty(noTxid);
    }

    public static <T> Mono<T> logTxId(DSLContext dsl, String label, T result) {
        return debugTxid(dsl, label).map(id -> result);
    }

}
