package orders.data.repository.r2dbc;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import orders.data.access.jooq.tables.Orders;
import orders.data.model.OrderEntity;
import orders.data.repository.OrderRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static orders.data.access.jooq.Tables.ORDERS;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderRepositoryR2DBC implements OrderRepository {
    DSLContext dslContext;

    private static OrderEntity toOrderEntity(Record record) {
        //    repeated Item items = 1;
        //    string customer_id = 2;
        //    Delivery delivery = 3;
        //
        //    message Item {
        //        string id = 1;
        //        string name = 2;
        //        double cost = 3;
        //    }
        return OrderEntity.builder()
                .id(record.get(ORDERS.ID))
                .build();
    }

    @Override
    public Mono<List<OrderEntity>> findAll() {
        return Flux.from(selectFromOrders(ORDERS))
                .map(OrderRepositoryR2DBC::toOrderEntity)
                .collectList();
    }

    @Override
    public Mono<OrderEntity> findById(UUID id) {
        return Flux.from(selectFromOrders(ORDERS).where(ORDERS.ID.eq(id))).next()
                .map(OrderRepositoryR2DBC::toOrderEntity);
    }

    private SelectJoinStep<Record> selectFromOrders(Orders orders) {
        return dslContext.select(orders.fields()).from(orders);
    }

}
