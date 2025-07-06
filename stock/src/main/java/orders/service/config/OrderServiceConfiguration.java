package orders.service.config;

import order.v1.OrderServiceGrpc.OrderServiceImplBase;
import orders.service.OrderServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderServiceConfiguration {

    @Bean
    public OrderServiceImplBase orderService() {
        return new OrderServiceImpl();
    }

}
