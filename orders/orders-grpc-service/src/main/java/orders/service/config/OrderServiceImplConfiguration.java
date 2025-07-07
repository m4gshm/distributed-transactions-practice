package orders.service.config;

import orders.service.OrderServiceImpl;
import orders.v1.OrderServiceGrpc.OrderServiceImplBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reserve.v1.ReserveServiceGrpc;
import reserve.v1.ReserveServiceGrpc.ReserveServiceFutureStub;
import reserve.v1.ReserveServiceGrpc.ReserveServiceStub;

@Configuration
public class OrderServiceImplConfiguration {

    @Bean
    public OrderServiceImplBase orderService(ReserveServiceStub reserveClient) {
        return new OrderServiceImpl(reserveClient);
    }

}
