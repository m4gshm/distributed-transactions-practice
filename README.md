# distributed-transactions-practice

1. cd ./docker && ./recreate.sh && cd ../
2. cd ./java
3. gradlew :payments:payments-grpc-service:bootRun
4. gradlew :reserve:reserve-grpc-service:bootRun
5. gradlew :orders:orders-grpc-service:bootRun
6. open in browser http://localhost:7080/swagger-ui/index.html
7. use examples from the [request](request) directory to create an order
