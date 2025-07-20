package payments.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payments.data.PaymentStorage;
import payments.service.PaymentsServiceImpl;
import payments.v1.PaymentsServiceGrpc.PaymentsServiceImplBase;

@Configuration
public class PaymentsServiceImplConfiguration {

    @Bean
    public PaymentsServiceImplBase orderService(PaymentStorage paymentStorage) {
        return new PaymentsServiceImpl(paymentStorage);
    }

}
