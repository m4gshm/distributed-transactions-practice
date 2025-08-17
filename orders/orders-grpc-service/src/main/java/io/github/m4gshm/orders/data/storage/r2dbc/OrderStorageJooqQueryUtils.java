package io.github.m4gshm.orders.data.storage.r2dbc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;

import java.time.OffsetDateTime;

import static java.util.Optional.ofNullable;
import static io.github.m4gshm.jooq.utils.Query.selectAll;
import static orders.data.access.jooq.Tables.DELIVERY;
import static orders.data.access.jooq.Tables.ORDERS;
import static org.jooq.JoinType.LEFT_OUTER_JOIN;

@Slf4j
@UtilityClass
public class OrderStorageJooqQueryUtils {

    public static OffsetDateTime orNow(OffsetDateTime value) {
        return ofNullable(value).orElseGet(OffsetDateTime::now);
    }

    public static SelectOnConditionStep<Record> selectOrdersJoinDelivery(DSLContext dsl) {
        return selectAll(dsl, ORDERS, DELIVERY)
                                               .from(ORDERS)
                                               .join(DELIVERY, LEFT_OUTER_JOIN)
                                               .on(DELIVERY.ORDER_ID.eq(ORDERS.ID));
    }
}
