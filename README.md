# distributed-transactions-practice

## Java start

1.  `cd ./docker && ./recreate.sh && cd ../`
2.  `cd ./java`
3.  `gradlew :payments:payments-grpc-service:bootRun &`
4.  `gradlew :reserve:reserve-grpc-service:bootRun &`
5.  `gradlew :orders:orders-grpc-service:bootRun &`
6.  open in browser http://localhost:7080/swagger-ui/index.html
7.  use examples from the [request](request) directory to create an order

## Run Go

1.  Just reuse DB populated by gralde build
2.  `cd ./golang`
3.  `task`
4.  `task run`
5.  open in browser http://localhost:8001/swagger-ui/index.html

## Comparsion

|   | Java | Go |
| --- | --- | --- |
| version | 24 | 1.25 |
| Build tool | Gradle 8.14 | Task |
| Code formatter | com.diffplug.spotless gradle plugin | _built-in_ |
| Boilerplate reducer (code gen) | org.projectlombok:lombok | fieldr |
| Logger | slf4j | zerolog |
| Application framework | Spring Boot | _not used_ |
| Dep. injection | Spring Context | _not used_ |
| Postgres driver | R2DBC | pgx/v5 |
| RDBC access layer generator | Jooq | Sqlc |
| DB migration lib | Liquibase | Goose |
| Tests engine | junit5 | _built-in_ |
| Integration tests | jvm-test-suite gradle plugin | \- |
| Mock lib | Mockito | Mockio |
| REST engine | Spring Webflux | _built-in http.Server_ |
| GRPC engine lib | io.grpc:grpc-netty-shaded | google.golang.org/grpc |
| GRPC code generator | com.google.protobuf gradle plugin | easyp |
| GRPC-REST transcoding | io.github.danielliu1123:grpc-server-boot-starter | grpc-gateway protoc plugin |
| GRPC-REST Open API generator | io.github.danielliu1123:grpc-starter-transcoding-springdoc | openapiv2 protoc plugin |
| Docker container builder | Gradle plugin com.bmuschko.docker-java-application | native |
| Kafka lib | Spring Kafka, reactor-kafka | franz-go |
| Tracing (Otel) | micrometer | ? |
| Otel exporter | spring-boot-micrometer-tracing-opentelemetry | go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrp |
| DB connection tracing | net.ttddyy.observation:datasource-micrometer | github.com/exaring/otelpgx |
| GRPC connection tracing | ? | ? |
| Prometheus http api provider | actuator | github.com/prometheus/client\_golang/prometheus/promhttp |
| Prometheus DB connection collector | actuator | github.com/cmackenzie1/pgxpool-prometheus |
| Config properties | Spring Boot (property files, env vars) | env vars |