package orders.service.config;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import orders.data.storage.OrderStorage;
import orders.service.OrdersServiceImpl;
import orders.v1.OrdersServiceGrpc.OrdersServiceImplBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceStub;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;

import static lombok.AccessLevel.PRIVATE;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class OrderServiceImplConfiguration {

    OrderStorage orderRepository;
    ReserveServiceStub reserveClient;
    PaymentsServiceStub paymentsClient;

    @Bean
    public OrdersServiceImplBase orderService() {
        return new OrdersServiceImpl(orderRepository, reserveClient, paymentsClient);
    }

}
