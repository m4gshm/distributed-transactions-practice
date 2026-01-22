import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    `java-library`
    id("org.springframework.boot")
    id("docker-conventions")
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":storage-api-reactive"))
    api(project(":grpc-service-webflux"))
    api(project(":protobuf-utils"))
    api(project(":tpc:tpc-grpc-service"))
    api(project(":postgres-prepared-transaction-r2dbc"))

    api(project(":grpc-client-reactive"))

    api(project(":orders:orders-storage-r2dbc"))
    api(project(":orders:orders-grpc-api"))
    api(project(":orders:orders-grpc-service-common"))

    api(project(":payments:payments-grpc-api"))
    api(project(":payments:payments-event-api"))
    api(project(":reserve:reserve-grpc-api"))
    api(project(":tpc:tpc-grpc-api"))
//    api(project(":idempotent-consumer"))
    api(project(":idempotent-consumer-r2dbc"))

    implementation("io.projectreactor.kafka:reactor-kafka")
//    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-kafka")

    implementation("org.postgresql:postgresql")

    implementation("io.projectreactor:reactor-core-micrometer")

}

tasks.named<Dockerfile>(DockerConventionJvmApplicationPlugin.DOCKERFILE_TASK_NAME) {
    exposePort(7080, 9080)
//    exposePort(5005, 5005)
}
