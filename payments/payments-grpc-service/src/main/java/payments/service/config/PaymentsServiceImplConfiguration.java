package payments.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payments.service.PaymentsServiceImpl;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceImplBase;

@Configuration
public class PaymentsServiceImplConfiguration {

    @Bean
    public PaymentsServiceImplBase orderService() {
        return new PaymentsServiceImpl();
    }

}
