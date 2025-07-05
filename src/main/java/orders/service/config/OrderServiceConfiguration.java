package orders.service.config;

import io.grpc.stub.StreamObserver;
import order.v1.OrderServiceGrpc.OrderServiceImplBase;
import order.v1.Orders.OrderCancelRequest;
import order.v1.Orders.OrderCancelResponse;
import order.v1.Orders.OrderCreateRequest;
import order.v1.Orders.OrderCreateResponse;
import order.v1.Orders.OrderFindRequest;
import order.v1.Orders.OrderFindResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderServiceConfiguration {

    @Bean
    public OrderServiceImplBase orderService() {
        return new OrderServiceImplBase() {
            @Override
            public void create(OrderCreateRequest request, StreamObserver<OrderCreateResponse> responseObserver) {
                super.create(request, responseObserver);
            }

            @Override
            public void cancel(OrderCancelRequest request, StreamObserver<OrderCancelResponse> responseObserver) {
                super.cancel(request, responseObserver);
            }

            @Override
            public void find(OrderFindRequest request, StreamObserver<OrderFindResponse> responseObserver) {
                super.find(request, responseObserver);
            }
        };
    }
}
