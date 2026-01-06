package io.github.m4gshm.postgres.prepared.transaction;

import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.ResultQuery;
import org.jooq.RowCountQuery;
import org.jooq.impl.DSL;

import java.time.OffsetDateTime;

@UtilityClass
public class TwoPhaseTransactionUtils {
    private static void checkTransactionId(String transactionId) {
        if (transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId cannot be blank");
        }
    }

    public static RowCountQuery commit(DSLContext dsl, String transactionId) {
        checkTransactionId(transactionId);
        return dsl.query("COMMIT PREPARED '" + transactionId + "'");
    }

    public static ResultQuery<Record> getPreparedById(DSLContext dsl, String transactionId) {
        checkTransactionId(transactionId);
        return dsl.resultQuery(
                "select transaction,gid,prepared from pg_prepared_xacts where database = current_database() and transaction = $1",
                transactionId);
    }

    public static ResultQuery<Record> listPrepared(DSLContext dsl) {
        return dsl.resultQuery(
                "select transaction,gid,prepared from pg_prepared_xacts where database = current_database()"
        );
    }

    public static PreparedTransaction newPreparedTransaction(Record r) {
        return PreparedTransaction.builder()
                .transaction(r.get(DSL.field("transaction ", Integer.class)))
                .gid(r.get(DSL.field("gid", String.class)))
                .prepared(r.get(DSL.field("prepared", OffsetDateTime.class)))
                .build();
    }

//    public static PreparedTransaction getPreparedById(DSLContext dsl, String transactionId) {
//        var records = dsl.resultQuery(
//                "select transaction,gid,prepared from pg_prepared_xacts where database = current_database() and transaction = $1",
//                transactionId);
//        return getPreparedTransaction(records);
//    }

//    public static void commit(DSLContext dsl, @NonNull String transactionId) {
//        dsl.query("COMMIT PREPARED '" + transactionId + "'").execute();
//    }

    public static RowCountQuery prepare(DSLContext dsl, String transactionId) {
        checkTransactionId(transactionId);
        return dsl.query("PREPARE TRANSACTION '" + transactionId + "'");
    }

//    public static List<PreparedTransaction> listPrepared(DSLContext dsl) {
//        var records = dsl.resultQuery(
//                "select transaction,gid,prepared from pg_prepared_xacts where database = current_database()"
//        );
//        return records.stream().map(TwoPhaseTransactionUtils::newPreparedTransaction).toList();
//    }

    public static RowCountQuery rollback(DSLContext dsl, String transactionId) {
        checkTransactionId(transactionId);
        return dsl.query("ROLLBACK PREPARED '" + transactionId + "'");
    }

//    private static PreparedTransaction newPreparedTransaction(org.jooq.Record r) {
//        return PreparedTransaction.builder()
//                .transaction(r.get(DSL.field("transaction ", Integer.class)))
//                .gid(r.get(DSL.field("gid", String.class)))
//                .prepared(r.get(DSL.field("prepared", OffsetDateTime.class)))
//                .build();
//    }

//    public static void prepare(DSLContext dsl, @NonNull String transactionId) {
//        checkTransactionId(transactionId);
//        dsl.query("PREPARE TRANSACTION '" + transactionId + "'").execute();
//    }
//
//    public static void rollback(DSLContext dsl, @NonNull String transactionId) {
//        checkTransactionId(transactionId);
//        dsl.query("ROLLBACK PREPARED '" + transactionId + "'").execute();
//    }

    public static class PrepareTransactionException extends RuntimeException {

        public final String id;

        public PrepareTransactionException(String id,
                Throwable throwable) {
            super(id, throwable);
            this.id = id;
        }
    }
}
