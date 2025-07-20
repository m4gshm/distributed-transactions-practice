package reserve.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reserve.data.ReserveStorage;
import reserve.service.ReserveServiceImpl;
import reserve.v1.ReserveServiceGrpc;

@Configuration
public class ReserveServiceImplConfiguration {

    @Bean
    public ReserveServiceGrpc.ReserveServiceImplBase orderService(ReserveStorage reserveStorage) {
        return new ReserveServiceImpl(reserveStorage);
    }

}
