plugins {
    `java-library`
    id("org.springframework.boot")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":storage-api"))
    api(project(":grpc-webflux"))
    api(project(":protobuf-utils"))
    api(project(":grpc-client"))

    api(project(":orders:orders-storage-r2dbc"))
    api(project(":orders:orders-grpc-api"))
    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))
    api(project(":reserve:reserve-grpc-api"))
    api(project(":tpc:tpc-grpc-api"))
    api(project(":idempotent-consumer"))

    implementation("io.projectreactor.kafka:reactor-kafka")
    implementation("org.springframework.kafka:spring-kafka")
}
