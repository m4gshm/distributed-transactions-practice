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

|   | Java (block io) | Java (reactive) | Go |
| --- | --- | --- | --- |
| version | 24 | 24 | 1.25 |
| Build tool | Gradle 8.14 | Gradle 8.14 | Task |
| Code formatter | com.diffplug.spotless gradle plugin | com.diffplug.spotless gradle plugin | _built-in_ |
| Boilerplate recuder (code gen) | org.projectlombok:lombok | org.projectlombok:lombok | fieldr |
| Logger | slf4j | slf4j | zerolog |
| Application framework | Spring Boot | Spring Boot | _not used_ |
| Dep. injection | Spring Context | Spring Context | _not used_ |
| Postgres driver | JDBC | R2DBC | pgx/v5 |
| RDBC access layer generator | Jooq | Jooq | Sqlc |
| DB migration lib | Liquibase | Liquibase | Goose |
| Tests engine | junit5 | junit5 | _built-in_ |
| Integration tests | jvm-test-suite gradle plugin | jvm-test-suite gradle plugin | \- |
| Mock lib | Mockito | Mockito | Mockio |
| REST engine | Spring MVC | Spring Webflux | _built-in http.Server_ |
| GRPC engine lib | okhttp | io.grpc:grpc-netty-shaded | google.golang.org/grpc |
| GRPC code generator | com.google.protobuf gradle plugin | com.google.protobuf gradle plugin | easyp |
| GRPC-REST transcoding | io.github.danielliu1123:grpc-server-boot-starter | io.github.danielliu1123:grpc-server-boot-starter | grpc-gateway protoc plugin |
| GRPC-REST Open API generator | io.github.danielliu1123:grpc-starter-transcoding-springdoc | io.github.danielliu1123:grpc-starter-transcoding-springdoc | openapiv2 protoc plugin |
| Docker container builder | Gradle plugin com.bmuschko.docker-java-application | Gradle plugin com.bmuschko.docker-java-application | native |
| Kafka lib | Spring Kafka | Spring Kafka, reactor-kafka | franz-go |
| Tracing (Otel) | micrometer | micrometer | ? |
| Otel exporter | spring-boot-micrometer-tracing-opentelemetry | spring-boot-micrometer-tracing-opentelemetry | go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrp |
| DB connection tracing | net.ttddyy.observation:datasource-micrometer | io.r2dbc:r2dbc-proxy | github.com/exaring/otelpgx |
| GRPC connection tracing | micrometer-core | micrometer-core | ? |
| Prometheus http api provider | actuator | actuator | github.com/prometheus/client\_golang/prometheus/promhttp |
| Prometheus DB connection collector | actuator | actuator | github.com/cmackenzie1/pgxpool-prometheus |
| Config properties | Spring Boot (property files, env vars) | Spring Boot (property files, env vars) | env vars |
