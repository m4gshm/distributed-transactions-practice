package orders.service.config;

import orders.service.OrdersServiceImpl;
import orders.v1.OrdersServiceGrpc.OrdersServiceImplBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceStub;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;

@Configuration
public class OrderServiceImplConfiguration {

    @Bean
    public OrdersServiceImplBase orderService(ReserveServiceStub reserveClient, PaymentsServiceStub paymentsClient) {
        return new OrdersServiceImpl(reserveClient, paymentsClient);
    }

}
