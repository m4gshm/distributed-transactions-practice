package orders.service.config;

import orders.service.OrderServiceImpl;
import orders.v1.OrderServiceGrpc;
import orders.v1.OrderServiceGrpc.OrderServiceImplBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderServiceConfiguration {

    @Bean
    public OrderServiceImplBase orderService() {
        return new OrderServiceImpl();
    }

}
