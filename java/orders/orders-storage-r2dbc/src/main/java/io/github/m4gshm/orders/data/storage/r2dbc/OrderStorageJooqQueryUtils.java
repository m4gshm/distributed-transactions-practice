package io.github.m4gshm.orders.data.storage.r2dbc;

import static java.util.Optional.ofNullable;
import static org.jooq.JoinType.LEFT_OUTER_JOIN;

import java.time.OffsetDateTime;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;

import io.github.m4gshm.orders.data.access.jooq.Tables;
import io.github.m4gshm.storage.jooq.Query;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class OrderStorageJooqQueryUtils {

    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }

    public static SelectOnConditionStep<Record> selectOrdersJoinDelivery(DSLContext dsl) {
        return Query.selectAll(dsl, Tables.ORDERS, Tables.DELIVERY)
                .from(Tables.ORDERS)
                .join(Tables.DELIVERY, LEFT_OUTER_JOIN)
                .on(Tables.DELIVERY.ORDER_ID.eq(Tables.ORDERS.ID));
    }
}
