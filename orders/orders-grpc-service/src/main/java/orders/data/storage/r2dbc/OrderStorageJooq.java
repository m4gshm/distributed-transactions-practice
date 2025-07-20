package orders.data.storage.r2dbc;

import jooq.utils.TwoPhaseTransaction;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.data.model.Order;
import orders.data.storage.OrderStorage;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static jooq.utils.Query.selectAllFrom;
import static lombok.AccessLevel.PRIVATE;
import static orders.data.access.jooq.Tables.ITEMS;
import static orders.data.access.jooq.Tables.ORDERS;
import static orders.data.storage.r2dbc.OrderStorageJooqMapperUtils.toOrder;
import static orders.data.storage.r2dbc.OrderStorageJooqQueryUtils.selectOrdersJoinDelivery;
import static orders.data.storage.r2dbc.OrderStorageJooqQueryUtils.storeOrder;
import static reactor.core.publisher.Mono.from;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderStorageJooq implements OrderStorage {
    DSLContext dsl;

    @Override
    public Mono<List<Order>> findAll() {
        return Flux.from(selectOrdersJoinDelivery(dsl))
                .map(record -> toOrder(record, record, List.of()))
                .collectList();
    }

    @Override
    public Mono<Order> findById(String id) {
        var selectOrder = selectOrdersJoinDelivery(dsl).where(ORDERS.ID.eq(id));
        var selectItems = selectAllFrom(dsl, ITEMS).where(ITEMS.ORDER_ID.eq(id));

        return from(selectOrder).zipWith(Flux.from(selectItems).collectList(), (order, items) -> {
            return toOrder(order, order, items);
        });
    }

    @Override
    public Mono<Order> save(Order order, boolean twoPhasedTransaction) {
        return from(dsl.transactionPublisher(trx -> {
            var dsl = trx.dsl();
            var routine = storeOrder(dsl, order);
            return !twoPhasedTransaction ? routine : TwoPhaseTransaction.prepare(routine, dsl, order.id());
        }));
    }
}
