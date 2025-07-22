package orders.service.config;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import orders.data.storage.OrderStorage;
import orders.service.OrdersServiceImpl;
import orders.v1.OrdersServiceGrpc.OrdersServiceImplBase;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payment.v1.PaymentServiceGrpc;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;
import tpc.v1.TwoPhaseCommitServiceGrpc;

import static lombok.AccessLevel.PRIVATE;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderServiceImplConfiguration {
    DSLContext dsl;
    OrderStorage orderRepository;
    ReserveServiceStub reserveClient;
    TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub reserveClientTcp;
    PaymentServiceGrpc.PaymentServiceStub paymentsClient;
    TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceStub paymentsClientTcp;

    @Bean
    public OrdersServiceImplBase orderService() {
        return new OrdersServiceImpl(dsl, orderRepository, reserveClient, reserveClientTcp,
                paymentsClient, paymentsClientTcp);
    }

}
