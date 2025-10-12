# distributed-transactions-practice


1. cd ./docker && ./recreate.sh && cd ../
2. cd ./java
3. gradlew :payments:payments-grpc-service:bootRun
4. gradlew :reserve:reserve-grpc-service:bootRun
5. gradlew :orders:orders-grpc-service:bootRun
6. open in browser http://localhost:7080/swagger-ui/index.html
7. use examples from the[request](request) directory to create an order

## Comparsion

|    | Java | Go |
|:---|:---|----|
| Build tool | Gradle 8.14 | Task |
| Code formatter | com.diffplug.spotless gradle plugin | *built-in* |
| Boilerplate recuder (code gen) | org.projectlombok:lombok | fieldr |
| Application framework | Spring Boot | *not used* |
| Dep. injection | Spring Context | *not used* |
| Postgres driver | R2DBC | pgx/v5 |
| RDBC access layer generator | Jooq | Sqlc |
| DB migration lib | Liquibase | Goose |
| Tests engine | junit5 | *built-in* |
| Integration tests |    |    |
| Mock lib | Mockito | Mockio |
| REST engine |    |    |
| GRPC engine |    |    |
| GRPC code generator |    |    |
| GRPC-RST transcoding | grpc-gateway protoc plugin | io.github.danielliu1123:grpc-server-boot-starter |
| Docker container builder |    |    |
| Kafka lib |    |    |
|    |    |    |
|    |    |    |


