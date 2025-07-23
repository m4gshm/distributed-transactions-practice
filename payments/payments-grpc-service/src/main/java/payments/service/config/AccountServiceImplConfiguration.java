package payments.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import payment.v1.AccountServiceGrpc;
import payments.data.AccountStorage;
import payments.service.AccountServiceImpl;

@Configuration
public class AccountServiceImplConfiguration {

    @Bean
    public AccountServiceGrpc.AccountServiceImplBase accountService(AccountStorage accountStorage) {
        return new AccountServiceImpl(accountStorage);
    }

}
