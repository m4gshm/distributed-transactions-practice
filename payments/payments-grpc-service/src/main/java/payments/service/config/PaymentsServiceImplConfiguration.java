package payments.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payment.v1.PaymentServiceGrpc;
import payments.data.AccountStorage;
import payments.data.PaymentStorage;
import payments.service.PaymentServiceImpl;

@Configuration
public class PaymentsServiceImplConfiguration {

    @Bean
    public PaymentServiceGrpc.PaymentServiceImplBase orderService(PaymentStorage paymentStorage,
                                                                  AccountStorage accountStorage) {
        return new PaymentServiceImpl(paymentStorage, accountStorage);
    }

}
